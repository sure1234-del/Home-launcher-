package com.mission.launcher

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local-first storage. Every collection is one JSON file in filesDir/data.
 * Writes are debounced, atomic (tmp + rename) and keep a .bak copy so a crash
 * mid-write can never destroy data. Nothing here needs the network.
 */
object Store {
    private lateinit var dir: File
    private val mem = HashMap<String, JSONArray>()
    private var cfgJ = JSONObject()
    private val dirty = HashSet<String>()
    private val ht = HandlerThread("store-io").also { it.start() }
    private val io = Handler(ht.looper)
    private val main = Handler(Looper.getMainLooper())
    val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var ready = false

    @Volatile var writeGeneration = 0L
        private set
    lateinit var app: Context

    fun init(c: Context) {
        synchronized(this) {
            if (ready) return
            app = c.applicationContext
            dir = File(app.filesDir, "data").apply { mkdirs() }
            cfgJ = readObj("cfg")
            ready = true
        }
        try {
            Migrate.run(app)
            SecureStore.migrateLegacy()
        } catch (e: Exception) {
            Log.e("migrate", e)
        }
    }

    // ---------- file helpers ----------
    private fun fileOf(n: String) = File(dir, "$n.json")

    private fun readText(n: String): String? {
        for (f in listOf(fileOf(n), File(dir, "$n.json.bak"))) {
            try {
                if (f.exists() && f.length() > 0) return f.readText()
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun readObj(n: String): JSONObject =
        try {
            JSONObject(readText(n) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    private fun readArr(n: String): JSONArray =
        try {
            JSONArray(readText(n) ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

    private fun writeAtomic(
        n: String,
        text: String,
        expectedGeneration: Long? = null,
    ) {
        try {
            synchronized(this) {
                if (expectedGeneration != null && expectedGeneration != writeGeneration) return
                val f = fileOf(n)
                val t = File(dir, "$n.json.tmp")
                t.writeText(text)
                if (f.exists()) f.copyTo(File(dir, "$n.json.bak"), true)
                if (!t.renameTo(f)) {
                    f.writeText(text)
                    t.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("store-write", e)
        }
    }

    private fun mark(n: String) {
        synchronized(this) { dirty.add(n) }
        io.removeCallbacks(flusher)
        io.postDelayed(flusher, 350)
    }

    private val flusher = Runnable { flushNow() }

    fun flushNow() {
        val writes: List<Pair<String, String>>
        val generation: Long
        synchronized(this) {
            generation = writeGeneration
            writes =
                dirty.mapNotNull { n ->
                    val text = if (n == "cfg") cfgJ.toString() else mem[n]?.toString()
                    text?.let { n to it }
                }
            dirty.clear()
        }
        writes.forEach { (name, text) -> writeAtomic(name, text, generation) }
    }

    fun eraseAll() {
        synchronized(this) {
            writeGeneration++
            io.removeCallbacks(flusher)
            val files = dir.listFiles() ?: throw IllegalStateException("Could not list stored data files")
            val failures = files.filter { it.exists() && !it.delete() }
            check(failures.isEmpty()) { "Could not delete ${failures.size} stored data files" }
            mem.clear()
            dirty.clear()
            cfgJ = JSONObject()
        }
        changed()
    }

    fun changed() {
        main.post {
            listeners.forEach {
                try {
                    it()
                } catch (e: Exception) {
                }
            }
        }
    }

    // ---------- settings (key/value) ----------
    fun s(
        k: String,
        d: String = "",
    ): String = synchronized(this) { if (cfgJ.has(k)) cfgJ.optString(k, d) else d }

    fun i(
        k: String,
        d: Int = 0,
    ): Int = synchronized(this) { cfgJ.optInt(k, d) }

    fun l(
        k: String,
        d: Long = 0L,
    ): Long = synchronized(this) { cfgJ.optLong(k, d) }

    fun f(
        k: String,
        d: Float = 0f,
    ): Float = synchronized(this) { cfgJ.optDouble(k, d.toDouble()).toFloat() }

    fun b(
        k: String,
        d: Boolean = false,
    ): Boolean = synchronized(this) { cfgJ.optBoolean(k, d) }

    fun has(k: String) = synchronized(this) { cfgJ.has(k) }

    fun put(
        k: String,
        v: Any?,
    ) {
        synchronized(this) { if (v == null) cfgJ.remove(k) else cfgJ.put(k, v) }
        mark("cfg")
    }

    fun putIfGeneration(
        expected: Long,
        k: String,
        v: Any?,
    ): Boolean {
        synchronized(this) {
            if (expected != writeGeneration) return false
            if (v == null) cfgJ.remove(k) else cfgJ.put(k, v)
            dirty.add("cfg")
        }
        io.removeCallbacks(flusher)
        io.postDelayed(flusher, 350)
        return true
    }

    fun cfgJson(): JSONObject = synchronized(this) { JSONObject(cfgJ.toString()) }

    fun setStringList(
        k: String,
        l: List<String>,
    ) = put(k, l.joinToString("\u001f"))

    fun stringList(k: String): List<String> = s(k).split("\u001f").filter { it.isNotEmpty() }

    // ---------- collections ----------
    private fun arr(n: String): JSONArray = mem.getOrPut(n) { readArr(n) }

    fun rows(n: String): List<JSONObject> =
        synchronized(this) {
            val a = arr(n)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }

    fun add(
        n: String,
        o: JSONObject,
    ): JSONObject {
        synchronized(this) {
            if (!o.has("id")) o.put("id", newId())
            arr(n).put(o)
        }
        mark(n)
        changed()
        return o
    }

    fun update(
        n: String,
        id: String,
        fn: (JSONObject) -> Unit,
    ): Boolean {
        var ok = false
        synchronized(this) {
            val a = arr(n)
            for (k in 0 until a.length()) {
                val o = a.optJSONObject(k) ?: continue
                if (o.optString("id") == id) {
                    fn(o)
                    ok = true
                    break
                }
            }
        }
        if (ok) {
            mark(n)
            changed()
        }
        return ok
    }

    fun remove(
        n: String,
        id: String,
    ): Boolean {
        var ok = false
        synchronized(this) {
            val a = arr(n)
            val out = JSONArray()
            for (k in 0 until a.length()) {
                val o = a.optJSONObject(k) ?: continue
                if (o.optString("id") == id) ok = true else out.put(o)
            }
            if (ok) mem[n] = out
        }
        if (ok) {
            mark(n)
            changed()
        }
        return ok
    }

    fun replace(
        n: String,
        list: List<JSONObject>,
    ) {
        synchronized(this) {
            val a = JSONArray()
            list.forEach { a.put(it) }
            mem[n] = a
        }
        mark(n)
        changed()
    }

    fun clear(n: String) = replace(n, emptyList())

    fun obj(n: String): JSONObject = synchronized(this) { readObj("o_$n") }

    fun saveObj(
        n: String,
        o: JSONObject,
        expectedGeneration: Long? = null,
    ) {
        writeAtomic("o_$n", o.toString(), expectedGeneration ?: writeGeneration)
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** Whole database as one JSON document (used by backup). */
    fun exportAll(): JSONObject {
        flushNow()
        val root = JSONObject()
        root.put("format", 2)
        root.put("exportedAt", System.currentTimeMillis())
        val c = cfgJson()
        for (k in SECRET_KEYS) c.remove(k)
        root.put("cfg", c)
        val cols = JSONObject()
        val names =
            dir.listFiles()?.map {
                it.name
            }?.filter { it.endsWith(".json") && it != "cfg.json" }?.map { it.removeSuffix(".json") } ?: emptyList()
        for (n in names) {
            if (n.startsWith("cache_") || n.startsWith("o_cache")) continue
            cols.put(n, if (n.startsWith("o_")) readObj(n) else synchronized(this) { arr(n) })
        }
        root.put("cols", cols)
        return root
    }

    fun importAll(
        root: JSONObject,
        merge: Boolean,
    ): Int {
        var n = 0
        val c = root.optJSONObject("cfg")
        if (c != null) {
            val keys = c.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in SECRET_KEYS) continue
                if (!merge || !has(k)) {
                    synchronized(this) { cfgJ.put(k, c.get(k)) }
                    n++
                }
            }
            mark("cfg")
        }
        val cols = root.optJSONObject("cols")
        if (cols != null) {
            val keys = cols.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = cols.opt(k)
                if (v is JSONArray) {
                    val have = if (merge) rows(k).map { it.optString("id") }.toSet() else emptySet()
                    val out = ArrayList<JSONObject>()
                    if (merge) out.addAll(rows(k))
                    for (j in 0 until v.length()) {
                        val o = v.optJSONObject(j) ?: continue
                        if (o.optString("id") !in have || o.optString("id").isEmpty()) {
                            out.add(o)
                            n++
                        }
                    }
                    replace(k, out)
                } else if (v is JSONObject && k.startsWith("o_")) {
                    writeAtomic(k, v.toString())
                    n++
                }
            }
        }
        changed()
        return n
    }

    val SECRET_KEYS = setOf("pin_hash", "reset_hash", "reset_salt", "reset_fail", "reset_lock_until", "ai_keys", "zite_key", "gemini_key")

    fun dataDir(): File = dir

    fun today(): String = LocalDate.now(Repo.zone()).toString()
}

object Log {
    fun dump(): String = Store.s("last_error")

    fun e(
        tag: String,
        t: Throwable,
    ) {
        try {
            android.util.Log.e("Mission/$tag", t.toString())
            Store.put("last_error", "$tag: ${t.javaClass.simpleName}: ${t.message}")
        } catch (x: Exception) {
        }
    }
}

fun JSONObject.str(
    k: String,
    d: String = "",
): String = optString(k, d)

fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun obj(vararg p: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    p.forEach { o.put(it.first, it.second) }
    return o
}
