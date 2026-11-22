package com.mission.launcher

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

object Http {
    fun call(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: JSONObject? = null,
        timeout: Int = 90000,
    ): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = timeout
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code >= 400) {
            throw Exception(
                "HTTP $code: " +
                    (
                        try {
                            JSONObject(txt).let { it.optJSONObject("error")?.optString("message") ?: it.optString("message", txt) }
                        } catch (
                            e: Exception,
                        ) {
                            txt
                        }
                    ).take(220),
            )
        }
        return JSONObject(txt)
    }
}

/**
 * Multi-provider AI. Chat history is stored locally forever; only a rolling summary + the latest
 * turns are sent, so conversations never hit a context limit.
 */
object Ai {
    class Provider(val id: String, val name: String, val kind: String, val base: String, val model: String, val needsBase: Boolean = false)

    val providers =
        listOf(
            Provider("gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-flash-latest"),
            Provider("openai", "OpenAI ChatGPT", "openai", "https://api.openai.com/v1", "gpt-4o-mini"),
            Provider("claude", "Anthropic Claude", "anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
            Provider("custom", "Custom (OpenAI-compatible)", "openai", "https://openrouter.ai/api/v1", "openrouter/auto", true),
        )

    fun provider(id: String = Store.s("ai_provider", "gemini")): Provider = providers.firstOrNull { it.id == id } ?: providers[0]

    fun key(id: String): String = SecureStore.get("ai:$id")

    fun setKey(
        id: String,
        k: String,
    ) = SecureStore.put("ai:$id", k.trim())

    fun model(p: Provider = provider()) = Store.s("ai_model_${p.id}", p.model).ifBlank { p.model }

    fun base(p: Provider = provider()) = Store.s("ai_base_${p.id}", p.base).ifBlank { p.base }.trimEnd('/')

    fun ready(p: Provider = provider()) = key(p.id).isNotBlank()

    // ---------- models ----------
    fun fetchModels(p: Provider): List<String> {
        val k = key(p.id)
        if (k.isBlank()) throw Exception("Add the ${p.name} key first")
        return when (p.kind) {
            "gemini" -> {
                val r = Http.call("GET", "${base(p)}/models?pageSize=200&key=$k", emptyMap(), null, 30000)
                (r.optJSONArray("models") ?: JSONArray()).objs()
                    .filter {
                            m ->
                        (m.optJSONArray("supportedGenerationMethods")?.toString() ?: "").contains("generateContent")
                    }.map { it.optString("name").removePrefix("models/") }
                    .filter { !it.contains("embedding") && !it.contains("tts") && !it.contains("image") && !it.contains("aqa") }.sortedDescending()
            }
            "anthropic" -> {
                val r =
                    Http.call(
                        "GET",
                        "${base(p)}/models?limit=100",
                        mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"),
                        null,
                        30000,
                    )
                (r.optJSONArray("data") ?: JSONArray()).objs().map { it.optString("id") }
            }
            else -> {
                val r = Http.call("GET", "${base(p)}/models", mapOf("Authorization" to "Bearer $k"), null, 30000)
                (r.optJSONArray("data") ?: JSONArray()).objs().map { it.optString("id") }.sorted()
            }
        }
    }

    // ---------- context ----------
    fun statusText(c: Context): String {
        val z = Zite.data(c)
        val today = LocalDate.now(Repo.zone())
        val tasks = Repo.tasksFor(today).joinToString("; ") { (if (it.optBoolean("done")) "[x] " else "[ ] ") + it.optString("text") }
        val mocks = Upcoming.items(c, 3).joinToString("; ") { it.title + " on " + it.date }
        return "Today $today. Days to Prelims ${java.time.temporal.ChronoUnit.DAYS.between(today, Repo.examDate())}. Studied today ${Repo.minutesOn(today)} min, week ${Repo.weekMinutes()} min, streak ${Repo.streak()} d. Water ${Repo.waterToday()}/${Water.goal()} ml. " +
            "Prelims syllabus ${(Syl.overall(z) * 100).toInt()}% done. Tasks: ${tasks.ifBlank { "none" }}. Next: ${mocks.ifBlank { "none" }}. Revisions due: ${Repo.revisionsDue().size}."
    }

    private fun system(c: Context) =
        "You are Mission AI, a concise study mentor inside the user's launcher. The user is preparing for UPSC Civil Services 2027 (Hindi medium, Philosophy optional). " +
            "Reply in the language the user writes in (Hindi, Hinglish or English), short and point-wise. Never invent facts or statistics; say when unsure. Live status: " + statusText(c) + (
                Store.s("ai_summary").let {
                    if (it.isBlank()) "" else "\nEarlier conversation summary: $it"
                }
            )

    // ---------- history ----------
    fun history(): List<JSONObject> = Store.rows("ai_chat").sortedBy { it.optLong("t") }

    fun clearHistory() {
        Store.clear("ai_chat")
        Store.put("ai_summary", "")
    }

    private fun push(
        role: String,
        text: String,
    ) {
        Store.add("ai_chat", obj("role" to role, "text" to text, "t" to System.currentTimeMillis(), "sum" to false))
    }

    /** keep requests small: fold old turns into a running summary (done by the model itself). */
    private fun compact(c: Context) {
        val live = history().filter { !it.optBoolean("sum") }
        val chars = live.sumOf { it.optString("text").length }
        if (chars < 24000 || live.size < 14) return
        val old = live.take(live.size - 8)
        val tx = old.joinToString("\n") { (if (it.optString("role") == "user") "User: " else "AI: ") + it.optString("text").take(1200) }
        try {
            val s =
                raw(
                    listOf(
                        "user" to ("Summarise this conversation in under 250 words, keeping facts, decisions and the user's preferences:\n" + Store.s("ai_summary") + "\n" + tx),
                    ),
                    "You write compact conversation summaries.",
                    false,
                    c,
                )
            if (s.isNotBlank()) {
                Store.put("ai_summary", s.take(2500))
                old.forEach {
                        o ->
                    Store.update("ai_chat", o.optString("id")) { it.put("sum", true) }
                }
            }
        } catch (e: Exception) {
        }
    }

    // ---------- public API ----------

    /** Blocking. Call from a background thread. */
    fun ask(
        c: Context,
        text: String,
    ): String {
        val p = provider()
        if (!ready(p)) throw Exception("Add your ${p.name} key in Settings > AI")
        compact(c)
        push("user", text)
        val msgs = ArrayList<Pair<String, String>>()
        history().filter { !it.optBoolean("sum") }.forEach {
                m ->
            val r = if (m.optString("role") == "user") "user" else "model"
            val t = m.optString("text")
            if (msgs.isNotEmpty() && msgs.last().first == r) {
                msgs[msgs.size - 1] = r to (msgs.last().second + "\n" + t)
            } else {
                msgs.add(
                    r to t,
                )
            }
        }
        while (msgs.isNotEmpty() && msgs.first().first != "user") msgs.removeAt(0)
        val tools = Store.b("ai_tools", true)
        val out =
            try {
                raw(msgs, system(c), tools, c)
            } catch (e: Exception) {
                if ((e.message ?: "").contains("long", true) || (e.message ?: "").contains("token", true)) {
                    raw(
                        msgs.takeLast(3).dropWhile {
                            it.first != "user"
                        },
                        system(c),
                        tools,
                        c,
                    )
                } else {
                    throw e
                }
            }
        push("model", out)
        return out
    }

    private fun raw(
        msgs: List<Pair<String, String>>,
        sys: String,
        tools: Boolean,
        c: Context,
    ): String {
        val p = provider()
        val k = key(p.id)
        return when (p.kind) {
            "gemini" -> gemini(p, k, msgs, sys, tools, c)
            "anthropic" -> {
                val arr = JSONArray()
                msgs.forEach { arr.put(obj("role" to (if (it.first == "user") "user" else "assistant"), "content" to it.second)) }
                val r =
                    Http.call(
                        "POST",
                        "${base(p)}/messages",
                        mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"),
                        obj("model" to model(p), "max_tokens" to 2048, "system" to sys, "messages" to arr),
                    )
                (r.optJSONArray("content") ?: JSONArray()).objs().joinToString("") { it.optString("text") }.ifBlank { "(empty reply)" }
            }
            else -> {
                val arr = JSONArray()
                arr.put(obj("role" to "system", "content" to sys))
                msgs.forEach { arr.put(obj("role" to (if (it.first == "user") "user" else "assistant"), "content" to it.second)) }
                val r =
                    Http.call(
                        "POST",
                        "${base(p)}/chat/completions",
                        mapOf("Authorization" to "Bearer $k"),
                        obj("model" to model(p), "messages" to arr),
                    )
                r.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.ifBlank { null } ?: "(empty reply)"
            }
        }
    }

    // ---------- Gemini with function calling ----------
    private fun decl(
        name: String,
        desc: String,
        props: JSONObject,
        req: List<String> = emptyList(),
    ) = obj(
        "name" to name,
        "description" to desc,
        "parameters" to obj("type" to "OBJECT", "properties" to props, "required" to JSONArray(req)),
    )

    private fun s(d: String) = obj("type" to "STRING", "description" to d)

    private fun tools(): JSONArray =
        JSONArray().put(
            obj(
                "functionDeclarations" to
                    JSONArray()
                        .put(decl("add_task", "Add a study task", obj("text" to s("task"), "date" to s("YYYY-MM-DD, default today"), "time" to s("HH:mm optional")), listOf("text")))
                        .put(decl("add_note", "Save a note", obj("text" to s("note text")), listOf("text")))
                        .put(decl("add_water", "Log water intake in ml", obj("ml" to obj("type" to "INTEGER", "description" to "millilitres")), listOf("ml")))
                        .put(decl("add_revision", "Add a topic to the spaced revision list", obj("topic" to s("topic")), listOf("topic")))
                        .put(decl("add_event", "Add a study-related upcoming event", obj("title" to s("title"), "date" to s("YYYY-MM-DD"), "time" to s("HH:mm optional")), listOf("title", "date")))
                        .put(decl("start_timer", "Start the floating study timer", obj("subject" to s("subject name")), listOf("subject")))
                        .put(decl("get_status", "Get today's study status", JSONObject())),
            ),
        )

    private fun runTool(
        c: Context,
        name: String,
        a: JSONObject,
    ): String =
        try {
            when (name) {
                "add_task" -> {
                    Repo.addTask(
                        a.optString("text"),
                        try {
                            LocalDate.parse(a.optString("date"))
                        } catch (
                            e: Exception,
                        ) {
                            LocalDate.now(Repo.zone())
                        },
                        a.optString("time"),
                    )
                    "Task added."
                }
                "add_note" -> {
                    Store.add("notes", obj("date" to Store.today(), "text" to a.optString("text"), "t" to System.currentTimeMillis()))
                    "Note saved."
                }
                "add_water" -> {
                    val ml = a.optInt("ml")
                    if (ml in 10..3000) {
                        Water.logFromAction(c, ml)
                        "Logged $ml ml."
                    } else {
                        "Invalid amount."
                    }
                }
                "add_revision" -> {
                    Repo.addRevision(a.optString("topic"))
                    "Added to revision."
                }
                "add_event" -> {
                    Store.add("events", obj("title" to a.optString("title"), "date" to a.optString("date"), "time" to a.optString("time")))
                    "Event added."
                }
                "start_timer" -> {
                    if (!TimerState.active()) {
                        TimerState.start(a.optString("subject", "General"), "stopwatch")
                        TimerService.start(c)
                        "Timer started."
                    } else {
                        "Timer already running."
                    }
                }
                "get_status" -> statusText(c)
                else -> "Unknown tool."
            }
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }

    private fun gemini(
        p: Provider,
        k: String,
        msgs: List<Pair<String, String>>,
        sys: String,
        useTools: Boolean,
        c: Context,
    ): String {
        val contents = JSONArray()
        msgs.forEach { contents.put(obj("role" to it.first, "parts" to JSONArray().put(obj("text" to it.second)))) }
        for (round in 0 until 5) {
            val body =
                obj(
                    "systemInstruction" to obj("parts" to JSONArray().put(obj("text" to sys))),
                    "contents" to contents,
                    "generationConfig" to obj("temperature" to 0.6),
                )
            if (useTools) body.put("tools", tools())
            val r = Http.call("POST", "${base(p)}/models/${model(p)}:generateContent?key=$k", emptyMap(), body)
            val cand = r.optJSONArray("candidates")?.optJSONObject(0)
            val parts = cand?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val calls = parts.objs().filter { it.has("functionCall") }
            if (calls.isEmpty()) {
                return parts.objs().joinToString("") {
                    it.optString("text")
                }.ifBlank { r.optJSONObject("promptFeedback")?.toString() ?: "(empty reply)" }
            }
            contents.put(obj("role" to "model", "parts" to parts))
            val resp = JSONArray()
            calls.forEach {
                    fc ->
                val f = fc.getJSONObject("functionCall")
                resp.put(
                    obj(
                        "functionResponse" to obj("name" to f.optString("name"), "response" to obj("result" to runTool(c, f.optString("name"), f.optJSONObject("args") ?: JSONObject()))),
                    ),
                )
            }
            contents.put(obj("role" to "user", "parts" to resp))
        }
        return "Done."
    }

    // ---------- other AI apps (Xiaomi HyperOS, Gemini app, ChatGPT ...) ----------
    class AiApp(val label: String, val pkg: String)

    private val known =
        listOf(
            "Google Gemini" to "com.google.android.apps.bard", "OpenAI ChatGPT" to "com.openai.chatgpt", "Anthropic Claude" to "com.anthropic.claude", "Perplexity" to "ai.perplexity.app.android", "Copilot" to "com.microsoft.copilot", "DeepSeek" to "com.deepseek.chat",
            "Xiao AI / HyperOS assistant" to "com.miui.voiceassist", "Xiaomi AI engine" to "com.xiaomi.aicr", "Mi AI vision" to "com.xiaomi.aiasst.vision",
        )

    fun installedAiApps(c: Context): List<AiApp> {
        val pm = c.packageManager
        val out = ArrayList<AiApp>()
        known.forEach {
                (l, p) ->
            try {
                pm.getPackageInfo(p, 0)
                if (pm.getLaunchIntentForPackage(p) != null) out.add(AiApp(l, p))
            } catch (
                e: Exception,
            ) {
            }
        }
        try {
            pm.getInstalledApplications(0).forEach { ai ->
                val l = ai.loadLabel(pm).toString()
                val low = l.lowercase()
                if ((low.contains("xiao ai") || low.contains("hyperai") || low.contains("hyper ai") || low.contains("xiaomi ai") || low.contains("mi ai")) &&
                    out.none {
                        it.pkg == ai.packageName
                    } && pm.getLaunchIntentForPackage(ai.packageName) != null
                ) {
                    out.add(AiApp(l, ai.packageName))
                }
            }
        } catch (
            e: Exception,
        ) {
        }
        return out
    }

    fun openApp(
        c: Context,
        pkg: String,
    ): Boolean =
        try {
            c.startActivity(c.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }

    fun assist(c: Context): Boolean =
        try {
            c.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }

    /** Send text to a chosen AI app through the share sheet target when it supports it. */
    fun shareTo(
        c: Context,
        pkg: String,
        text: String,
    ): Boolean =
        try {
            c.startActivity(
                Intent(
                    Intent.ACTION_SEND,
                ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (
            e: Exception,
        ) {
            false
        }
}
