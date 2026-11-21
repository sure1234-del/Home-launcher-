package com.mission.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.temporal.ChronoUnit

// ======================= models =======================
class Mock(val title: String, val section: String, val kind: String, val date: LocalDate?, val q: Int, val status: String) {
    val code: String get() = title.substringBefore(" ").let { title.split(" ").getOrNull(1) ?: it }
    val ready: Boolean get() = status.equals("Ready", true)
}
class Attempt(val title: String, val mode: String, val at: Long, val score: Double, val c: Int, val w: Int, val s: Int, val acc: Double, val sec: Int, val lim: Int) {
    val isMock: Boolean get() = title.startsWith("Mock") || title.startsWith("Sectional")
    val isPaper: Boolean get() = mode.equals("Full Paper", true)
}
class Sub(val name: String, val q: Int)
class Micro(val name: String, val subs: List<Sub>) { val q: Int get() = subs.sumOf { it.q } }
class Subj(val name: String, val micros: List<Micro>) { val q: Int get() = micros.sumOf { it.q } }
class ZData(
    val mocks: List<Mock>,
    val attempts: List<Attempt>,
    val syl: List<Subj>,
    val subjResp: Map<String, IntArray>,
    val microResp: Map<String, IntArray>,
    val pyqYears: Map<Int, Map<String, Int>>,
    val updated: Long,
    val live: Boolean
)

// ======================= Zite =======================
object Zite {
    const val BASE = "https://tables.zite.com/api/v1"
    const val DEFAULT_DB = "4ac0c0b6fa0504e9"
    const val PORTAL = "https://app.zite.com/workspace/upsc-prelims-pyq-analyzer-migration-4ac0c0b6fa0504e9"
    private val T_MOCK = "tmdHZ5f4Dmi"; private val T_ATT = "twCUDF8M3Uk"; private val T_RESP = "tdro4b981tN"; private val T_PYQ = "tmQwv2XY5gm"
    private val F = mapOf(
        "Title" to "fw6dYGDAcBs", "Section" to "fd6D42USKdM", "Kind" to "f1f3544Ckrr", "Status" to "fozWMT78fvN", "Scheduled Date" to "f76AzV9FQAs", "Question Count" to "fxnBBmjL4x7",
        "Mode" to "fi2m24hvs4p", "Score" to "ftvW3abUZEH", "Correct" to "fpcRNkKVKpX", "Wrong" to "fsW6KfXcxP5", "Skipped" to "fgQv9h2KU9s", "Accuracy" to "fdY5LJVY6No", "Year" to "f7dMVCcvBnN",
        "Time Taken Sec" to "fbMzT3KnBFN", "Time Limit Sec" to "fqD5Gbvke1o", "Submitted At" to "f312NXkXTsG", "Result" to "fnwXK1JDaj6", "Subject" to "fmus9ptLZPh", "Micro Theme" to "frzmZ7LZMf2", "Sub Topic" to "fg4gC83iHYe")

    @Volatile private var memo: ZData? = null
    @Volatile var syncing = false
    @Volatile var lastError: String = ""

    fun hasKey() = SecureStore.get("zite").isNotBlank()
    fun db() = Store.s("zite_db", DEFAULT_DB).ifBlank { DEFAULT_DB }
    fun data(c: Context): ZData { memo?.let { return it }; val d = load(c); memo = d; return d }
    fun invalidate() { memo = null }

    // ---- snapshot + cache loading ----
    private fun asset(c: Context, n: String): String = try { c.assets.open(n).bufferedReader().use { it.readText() } } catch (e: Exception) { "[]" }
    private fun load(c: Context): ZData {
        val cache = Store.obj("cache_zite")
        val live = cache.has("mocks")
        val mocksJ = if (live) cache.optJSONArray("mocks") ?: JSONArray() else JSONArray(asset(c, "mocks.json"))
        val attJ = if (live) cache.optJSONArray("attempts") ?: JSONArray() else JSONArray(asset(c, "attempts.json"))
        val mocks = mocksJ.objs().map { Mock(it.optString("t"), it.optString("sec"), it.optString("kind"), parseDate(it.optString("d")), it.optInt("q"), it.optString("st")) }.sortedBy { it.date ?: LocalDate.MAX }
        val atts = attJ.objs().map { Attempt(it.optString("t"), it.optString("mode"), it.optLong("at"), it.optDouble("score", 0.0), it.optInt("c"), it.optInt("w"), it.optInt("s"), it.optDouble("acc", 0.0), it.optInt("sec"), it.optInt("lim")) }
        val sylJ = if (cache.has("syl")) cache.optJSONArray("syl") else JSONArray(asset(c, "syllabus.json"))
        val syl = parseTree(sylJ ?: JSONArray())
        val sr = HashMap<String, IntArray>(); val mr = HashMap<String, IntArray>()
        val srJ = if (live && cache.has("subjResp")) cache.optJSONObject("subjResp") else try { JSONObject(asset(c, "responses.json")) } catch (e: Exception) { JSONObject() }
        srJ?.keys()?.forEach { k -> val a = srJ.optJSONArray(k); if (a != null) sr[k] = intArrayOf(a.optInt(0), a.optInt(1), a.optInt(2)) }
        cache.optJSONObject("microResp")?.let { m -> m.keys().forEach { k -> val a = m.optJSONArray(k); if (a != null) mr[k] = intArrayOf(a.optInt(0), a.optInt(1), a.optInt(2)) } }
        val pyqYears = LinkedHashMap<Int, Map<String, Int>>()
        cache.optJSONObject("pyq_years")?.let { years ->
            years.keys().forEach { year ->
                val subjects = LinkedHashMap<String, Int>()
                years.optJSONObject(year)?.let { counts -> counts.keys().forEach { subject -> subjects[subject] = counts.optInt(subject) } }
                year.toIntOrNull()?.let { pyqYears[it] = subjects }
            }
        }
        return ZData(mocks, atts, syl, sr, mr, pyqYears, cache.optLong("updated", 0L), live)
    }
    private fun parseTree(a: JSONArray): List<Subj> = a.objs().map { s -> Subj(s.optString("s"), (s.optJSONArray("m") ?: JSONArray()).objs().map { m -> Micro(m.optString("n"), (m.optJSONArray("t") ?: JSONArray()).objs().map { t -> Sub(t.optString("n"), t.optInt("q")) }) }) }
    private fun parseDate(s: String): LocalDate? = try { if (s.isBlank()) null else LocalDate.parse(s.take(10)) } catch (e: Exception) { null }

    // ---- REST ----
    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL("$BASE$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 45000; c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer " + SecureStore.get("zite").trim()); c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code >= 400) throw Exception("HTTP $code " + txt.take(160))
        return JSONObject(txt)
    }
    private fun recordsOf(r: JSONObject): JSONArray = r.optJSONArray("records") ?: r.optJSONArray("data") ?: r.optJSONArray("items") ?: JSONArray()
    private val errs = ArrayList<String>()
    private fun safeList(t: String): List<JSONObject>? = try { listAll(t) } catch (e: Exception) { errs.add(t + ": " + (e.message ?: e.javaClass.simpleName).take(100)); null }
    private fun listAll(table: String): List<JSONObject> {
        val out = ArrayList<JSONObject>(); var next: Any? = null; var limit = 500; val seen = HashSet<String>()
        for (page in 0 until 25) {
            val b = JSONObject().put("limit", limit); if (next != null) b.put("offset", next)
            val r = try { post("/bases/${db()}/tables/$table/records/list", b) } catch (e: Exception) { if (page == 0 && limit > 100) { limit = 100; b.put("limit", 100); post("/bases/${db()}/tables/$table/records/list", b) } else throw e }
            val recs = recordsOf(r); var fresh = 0
            for (i in 0 until recs.length()) recs.optJSONObject(i)?.let { x -> val id = x.optString("id"); if (id.isEmpty() || seen.add(id)) { out.add(x); fresh++ } }
            if (fresh == 0) break
            val n = r.opt("nextOffset") ?: r.opt("next_offset") ?: r.opt("offset")
            if (recs.length() < limit && (n == null || n == JSONObject.NULL)) break
            if (recs.length() == 0) break
            next = if (n != null && n != JSONObject.NULL) n else out.size
            if (recs.length() < limit) break
        }
        return out
    }
    private fun unwrap(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is JSONObject -> v.optString("label").ifEmpty { v.optString("name").ifEmpty { v.optString("value").ifEmpty { v.optString("title").ifEmpty { v.optString("url") } } } }
        is JSONArray -> if (v.length() == 0) "" else unwrap(v.opt(0))
        else -> v.toString()
    }
    private fun fv(rec: JSONObject, name: String): String {
        val f = rec.optJSONObject("fields") ?: rec
        val id = F[name]
        val alternateIds = if (name == "Subject") listOf("fpbZq46JPA3") else emptyList()
        return when {
            f.has(name) -> unwrap(f.opt(name))
            id != null && f.has(id) -> unwrap(f.opt(id))
            else -> alternateIds.firstNotNullOfOrNull { fieldId -> if (f.has(fieldId)) unwrap(f.opt(fieldId)) else null } ?: ""
        }
    }
    private fun num(rec: JSONObject, name: String): Double = fv(rec, name).toDoubleOrNull() ?: 0.0

    /** Pull everything we show on the home pages. Returns a human message. Safe to call from any thread. */
    fun sync(c: Context, full: Boolean = false): String {
        if (!hasKey()) return "No Zite API key yet (Settings > Zite)"
        if (syncing) return "Sync already running"
        syncing = true; errs.clear()
        val generation = Store.writeGeneration
        try {
            val cache = Store.obj("cache_zite")
            // mocks
            val mocks = JSONArray(); (safeList(T_MOCK) ?: emptyList()).forEach { r ->
                val t = fv(r, "Title"); if (t.isNotBlank()) mocks.put(obj("t" to t, "sec" to fv(r, "Section"), "kind" to fv(r, "Kind"), "d" to fv(r, "Scheduled Date").take(10), "q" to num(r, "Question Count").toInt(), "st" to fv(r, "Status"))) }
            if (mocks.length() > 0) cache.put("mocks", mocks)
            // attempts (submitted only)
            val attRows = safeList(T_ATT); val atts = JSONArray(); (attRows ?: emptyList()).forEach { r ->
                if (fv(r, "Status").equals("Submitted", true)) atts.put(obj("t" to fv(r, "Title"), "mode" to fv(r, "Mode"), "at" to parseInstant(fv(r, "Submitted At")), "score" to num(r, "Score"), "c" to num(r, "Correct").toInt(), "w" to num(r, "Wrong").toInt(), "s" to num(r, "Skipped").toInt(), "acc" to num(r, "Accuracy"), "sec" to num(r, "Time Taken Sec").toInt(), "lim" to num(r, "Time Limit Sec").toInt())) }
            if (attRows != null) cache.put("attempts", atts)
            // responses -> per subject / micro theme accuracy
            val sr = HashMap<String, IntArray>(); val mr = HashMap<String, IntArray>()
            val respRows = safeList(T_RESP); (respRows ?: emptyList()).forEach { r ->
                val s = fv(r, "Subject"); val m = fv(r, "Micro Theme"); val res = fv(r, "Result").lowercase()
                val idx = when (res) { "correct" -> 0; "wrong" -> 1; "skipped" -> 2; else -> -1 }
                if (idx >= 0 && s.isNotBlank()) { sr.getOrPut(s) { IntArray(3) }[idx]++; if (m.isNotBlank()) mr.getOrPut("$s|$m") { IntArray(3) }[idx]++ } }
            if (respRows != null) cache.put("subjResp", JSONObject().also { o -> sr.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) } })
            if (respRows != null) cache.put("microResp", JSONObject().also { o -> mr.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) } })
            val refreshTree = full || System.currentTimeMillis() - cache.optLong("treeAt", 0) > 12 * 3600000L
            val pyqRows = if (refreshTree || !cache.has("pyq_years")) safeList(T_PYQ) else null
            if (pyqRows != null) {
                val yearCounts = LinkedHashMap<Int, LinkedHashMap<String, Int>>()
                pyqRows.forEach { r ->
                    val year = num(r, "Year").toInt()
                    val subject = fv(r, "Subject").ifBlank { "Unclassified" }
                    if (year in 1950..2100) {
                        val counts = yearCounts.getOrPut(year) { LinkedHashMap() }
                        counts[subject] = (counts[subject] ?: 0) + 1
                    }
                }
                if (yearCounts.isNotEmpty()) {
                    cache.put("pyq_years", JSONObject().also { years ->
                        yearCounts.toSortedMap().forEach { (year, subjects) ->
                            years.put(year.toString(), JSONObject().also { counts ->
                                subjects.toSortedMap().forEach { (subject, count) -> counts.put(subject, count) }
                            })
                        }
                    })
                }
            }
            // Syllabus tree and year breakdown use the same PYQ records.
            if (refreshTree && pyqRows != null) {
                val tree = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Int>>>()
                pyqRows.forEach { r ->
                    val s = fv(r, "Subject").ifBlank { "Unclassified" }
                    val m = fv(r, "Micro Theme").ifBlank { "General" }
                    val t = fv(r, "Sub Topic")
                    val mm = tree.getOrPut(s) { LinkedHashMap() }.getOrPut(m) { LinkedHashMap() }
                    mm[t] = (mm[t] ?: 0) + 1
                }
                if (tree.isNotEmpty()) {
                    val a = JSONArray()
                    tree.toSortedMap().forEach { (s, ms) ->
                        a.put(obj("s" to s, "m" to JSONArray().also { ma ->
                            ms.toSortedMap().forEach { (m, ts) ->
                                ma.put(obj("n" to m, "t" to JSONArray().also { ta ->
                                    ts.toSortedMap().forEach { (t, q) -> ta.put(obj("n" to t, "q" to q)) }
                                }))
                            }
                        }))
                    }
                    cache.put("syl", a)
                    cache.put("treeAt", System.currentTimeMillis())
                }
            }
            cache.put("updated", System.currentTimeMillis())
            if (generation != Store.writeGeneration) return "Sync stopped because data was reset"
            Store.saveObj("cache_zite", cache, generation); invalidate()
            if (generation != Store.writeGeneration) return "Sync stopped because data was reset"
            if (errs.isEmpty()) {
                lastError = ""
                if (!Store.putIfGeneration(generation, "zite_last_err", "") ||
                    !Store.putIfGeneration(generation, "zite_last_ok", System.currentTimeMillis())
                ) return "Sync stopped because data was reset"
                Store.changed()
                return "Synced ${mocks.length()} mocks, ${atts.length()} results"
            }
            lastError = errs.joinToString("; ")
            if (!Store.putIfGeneration(generation, "zite_last_err", lastError)) return "Sync stopped because data was reset"
            Store.changed()
            return "Partly synced. " + lastError
        } catch (e: Exception) {
            if (generation != Store.writeGeneration) return "Sync stopped because data was reset"
            lastError = e.message ?: e.toString()
            Store.putIfGeneration(generation, "zite_last_err", lastError)
            return "Sync failed: $lastError"
        } finally { syncing = false }
    }
    private fun parseInstant(s: String): Long = try { Instant.parse(s).toEpochMilli() } catch (e: Exception) { try { OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (x: Exception) { System.currentTimeMillis() } }
    fun stale(): Boolean = System.currentTimeMillis() - Store.l("zite_last_ok", 0) > 3600000L - 60000L
}

// ======================= syllabus =======================
object Syl {
    enum class St { NEW, LEARNING, DONE }
    fun doneSet(): MutableSet<String> = Store.stringList("syl_done").toMutableSet()
    fun toggle(key: String) { val s = doneSet(); if (!s.add(key)) s.remove(key); Store.setStringList("syl_done", s.toList()) }
    fun microState(z: ZData, subj: String, micro: String): St {
        if ("$subj|$micro" in doneSet()) return St.DONE
        val r = z.microResp["$subj|$micro"] ?: return St.NEW
        val att = r[0] + r[1]; if (att == 0) return St.NEW
        return St.LEARNING
    }
    fun subjectProgress(z: ZData, s: Subj): Float {
        if (s.micros.isEmpty()) return 0f
        val d = doneSet(); var acc = 0f
        s.micros.forEach { m -> acc += when (microState(z, s.name, m.name)) { St.DONE -> 1f; St.LEARNING -> 0.4f; else -> 0f } }
        return (acc / s.micros.size).coerceIn(0f, 1f)
    }
    fun overall(z: ZData): Float { val t = z.syl.sumOf { it.micros.size }; if (t == 0) return 0f; return (z.syl.sumOf { (subjectProgress(z, it) * it.micros.size).toDouble() } / t).toFloat() }
    fun short(n: String) = when {
        n.startsWith("Economy") -> "Economy"; n.startsWith("Geography") -> "Geography"; n.startsWith("Polity") -> "Polity"; n.startsWith("Environment") -> "Environment"
        n.startsWith("Science") -> "Sci-Tech"; n.startsWith("History") -> "History"; n.startsWith("Current") -> "Curr. Affairs"; n.startsWith("International") -> "Intl. Relations"
        n.startsWith("Art") -> "Art & Culture"; n.startsWith("Agri") -> "Agriculture"; else -> n }
}

/** Mains syllabus (manual tracking) - GS1-4, Essay and Philosophy optional. */
object Mains {
    class Paper(val name: String, val color: Int, val topics: List<String>)
    val papers: List<Paper> = listOf(
        Paper("GS-1", T.terra, listOf("Indian art forms, literature, architecture", "Modern India: 1750s to 1947", "Freedom struggle: stages and contributors", "Post-independence consolidation", "World history: industrial revolution, world wars", "Colonisation and decolonisation", "Indian society: diversity, women, population", "Poverty, urbanisation, globalisation, communalism", "Physical geography: geomorphology, climate, oceans", "Resource and economic geography, industries", "Human geography, urbanisation, disasters")),
        Paper("GS-2", T.plum, listOf("Constitution: features, amendments, basic structure", "Union and States: federalism, devolution", "Separation of powers, dispute redressal", "Parliament, state legislatures, executive, judiciary", "Representation of People Act, constitutional bodies", "Government policies and welfare schemes", "Health, education, human resources", "Poverty and hunger", "Governance: transparency, e-governance, citizen charters", "Role of civil services", "International relations: neighbourhood, groupings, diaspora")),
        Paper("GS-3", T.amber, listOf("Indian economy: planning, growth, inclusion", "Budgeting and fiscal policy", "Agriculture: cropping, irrigation, MSP, PDS", "Food processing, land reforms", "Industrial policy, infrastructure, investment models", "Science and technology: space, biotech, IT, nano, IPR", "Environment: conservation, EIA, disaster management", "Internal security: extremism, borders, cyber, organised crime")),
        Paper("GS-4", T.rose, listOf("Ethics and human interface", "Attitude, aptitude and foundational values", "Emotional intelligence", "Moral thinkers, Indian and Western", "Public service values", "Probity in governance", "Case studies practice")),
        Paper("Essay", T.sage, listOf("Philosophical and abstract themes", "Social and political themes", "Economy, environment and technology themes", "Structure, introduction and conclusion drills", "Timed essay practice")),
        Paper("Phil P-I", T.teal, listOf("Plato and Aristotle", "Rationalism: Descartes, Spinoza, Leibniz", "Empiricism: Locke, Berkeley, Hume", "Kant", "Hegel", "Moore, Russell, early Wittgenstein", "Logical positivism, later Wittgenstein, Quine", "Phenomenology, existentialism, Heidegger", "Carvaka, Jainism, Buddhism", "Nyaya, Vaisheshika, Samkhya, Yoga", "Mimamsa, Advaita, Vishishtadvaita, Dvaita")),
        Paper("Phil P-II", T.clay, listOf("Social and political ideals: justice, liberty, equality", "Sovereignty, individual and state, rights, duties", "Democracy, secularism, humanism", "Social change, gender, caste and class", "Violence, terrorism, colonialism, Marxism", "Gandhi, Ambedkar, Tagore, Aurobindo", "God: nature and proofs, problem of evil", "Soul, rebirth, liberation, immortality", "Religious language, faith and reason, pluralism")))
    fun key(p: Paper, i: Int) = "M|${p.name}|$i"
    fun progress(p: Paper): Float { val d = Syl.doneSet(); return p.topics.indices.count { key(p, it) in d }.toFloat() / p.topics.size }
    fun overall(): Float { val t = papers.sumOf { it.topics.size }; val d = Syl.doneSet(); return papers.sumOf { p -> p.topics.indices.count { key(p, it) in d } }.toFloat() / t }
}

// ======================= repository helpers =======================
object Repo {
    private fun ld(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: Exception) { null }
    fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun examDate(): LocalDate = ld(Store.s("exam_date", "2027-05-23")) ?: LocalDate.of(2027, 5, 23)
    fun examSecondsLeft(): Long = ChronoUnit.SECONDS.between(LocalDateTime.now(zone()), examDate().atTime(9, 30)).coerceAtLeast(0)

    // tasks
    fun csv(): String { val sb = StringBuilder("date,start,end,minutes,subject,kind\n"); Store.rows("sessions").sortedBy { it.optLong("start") }.forEach { s -> sb.append(dayOf(s.optLong("start"))).append(',').append(java.time.Instant.ofEpochMilli(s.optLong("start")).atZone(zone()).toLocalTime().withNano(0)).append(',').append(java.time.Instant.ofEpochMilli(s.optLong("end")).atZone(zone()).toLocalTime().withNano(0)).append(',').append(s.optInt("mins")).append(',').append(s.optString("subject").replace(",", " ")).append(',').append(s.optString("kind")).append('\n') }; return sb.toString() }
    fun carryOver(): Int { var n = 0; val today = LocalDate.now(zone()); val tmr = today.plusDays(1).toString(); tasksFor(today).filter { !it.optBoolean("done") }.forEach { t -> Store.update("tasks", t.optString("id")) { it.put("date", tmr) }; n++ }; return n }
    fun allTasks(): List<JSONObject> = Store.rows("tasks")
    fun tasksFor(d: LocalDate): List<JSONObject> = Store.rows("tasks").filter { ld(it.optString("date")) == d }
    fun openTasks(): List<JSONObject> { val t = LocalDate.now(zone()); return Store.rows("tasks").filter { val d = ld(it.optString("date")); !it.optBoolean("done") && d != null && !d.isAfter(t.plusDays(3)) }.sortedBy { it.optString("date") + it.optString("time") } }
    fun addTask(text: String, date: LocalDate = LocalDate.now(zone()), time: String = "") = Store.add("tasks", obj("date" to date.toString(), "text" to text, "done" to false, "time" to time))
    fun toggleTask(id: String) = Store.update("tasks", id) { it.put("done", !it.optBoolean("done")) }

    // study sessions
    fun addSession(start: Long, end: Long, subject: String, kind: String) {
        val m = ((end - start) / 60000L).toInt(); if (m < 1) return
        Store.add("sessions", obj("start" to start, "end" to end, "mins" to m, "subject" to subject, "kind" to kind))
    }
    fun minutesOn(d: LocalDate): Int = Store.rows("sessions").filter { dayOf(it.optLong("start")) == d }.sumOf { it.optInt("mins") }
    fun byDaySubject(days: List<LocalDate>): List<Map<String, Int>> {
        val rows = Store.rows("sessions"); return days.map { d -> rows.filter { dayOf(it.optLong("start")) == d }.groupBy { it.optString("subject").ifBlank { "General" } }.mapValues { e -> e.value.sumOf { it.optInt("mins") } } }
    }
    fun streak(): Int { var d = LocalDate.now(zone()); if (minutesOn(d) == 0) d = d.minusDays(1); var s = 0; while (minutesOn(d) > 0 && s < 1000) { s++; d = d.minusDays(1) }; return s }
    fun weekMinutes(): Int { val t = LocalDate.now(zone()); val mon = t.minusDays((t.dayOfWeek.value - 1).toLong()); return (0..6).sumOf { minutesOn(mon.plusDays(it.toLong())) } }

    // revisions (spaced repetition 1,3,7,21,60 days)
    val OFF = intArrayOf(1, 3, 7, 21, 60)
    fun revisionsDue(): List<JSONObject> = Store.rows("revisions").filter { val r = it.optInt("round"); r < OFF.size && ld(it.optString("last"))?.plusDays(OFF[r].toLong())?.isAfter(LocalDate.now(zone())) == false }
    fun addRevision(topic: String) = Store.add("revisions", obj("topic" to topic, "last" to LocalDate.now(zone()).toString(), "round" to 0))
    fun reviseDone(id: String) { Store.update("revisions", id) { it.put("last", LocalDate.now(zone()).toString()); it.put("round", it.optInt("round") + 1) }; Store.add("revlog", obj("date" to LocalDate.now(zone()).toString())) }

    // water
    fun waterToday(): Int = waterOn(LocalDate.now(zone()))
    fun waterOn(d: LocalDate): Int = Store.rows("water").filter { dayOf(it.optLong("t")) == d }.sumOf { it.optInt("ml") }.coerceAtLeast(0)

    // schedule
    fun scheduleFor(dow: Int): List<JSONObject> = Store.rows("schedule").filter { it.optInt("dow") == dow }.sortedBy { it.optString("start") }
}

// ======================= upcoming (study relevant only) =======================
class Up(val date: LocalDate, val time: String, val title: String, val sub: String, val icon: String, val color: Int)

object Upcoming {
    fun items(c: Context, limit: Int = 8): List<Up> {
        val today = LocalDate.now(Repo.zone()); val out = ArrayList<Up>()
        Zite.data(c).mocks.forEach { m -> val d = m.date ?: return@forEach; if (!d.isBefore(today)) out.add(Up(d, "", m.title, (if (m.ready) "Ready" else "Questions pending") + (if (m.q > 0) " - ${m.q} Qs" else ""), "target", if (m.ready) T.terra else T.text3)) }
        Store.rows("events").forEach { e -> val d = try { LocalDate.parse(e.optString("date")) } catch (x: Exception) { return@forEach }; if (!d.isBefore(today)) out.add(Up(d, e.optString("time"), e.optString("title"), e.optString("note"), "calendar", T.plum)) }
        Store.rows("tasks").forEach { t -> val d = try { LocalDate.parse(t.optString("date")) } catch (x: Exception) { return@forEach }; if (d.isAfter(today) && !t.optBoolean("done") && d.isBefore(today.plusDays(15))) out.add(Up(d, t.optString("time"), t.optString("text"), "Task", "checkc", T.sage)) }
        return out.sortedWith(compareBy({ it.date }, { it.time })).take(limit)
    }
    fun label(d: LocalDate): String { val n = ChronoUnit.DAYS.between(LocalDate.now(Repo.zone()), d); return when (n) { 0L -> "Today"; 1L -> "Tomorrow"; else -> d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + d.dayOfMonth + " " + d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " - in $n d" } }
}
