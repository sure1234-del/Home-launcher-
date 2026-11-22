package com.mission.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.security.MessageDigest

/**
 * Backups never leave the device unless you choose a destination.
 * 1) Local copies in app storage.
 * 2) Optional folder you pick with the system file picker - choose a Google Drive folder there
 *    and every daily backup is written straight into your Drive (no OAuth keys needed).
 * 3) Android Auto Backup (Google One backup of the phone) is enabled for the whole data folder.
 */
object Backup {
    private fun dir(c: Context) = File(c.filesDir, "backups").apply { mkdirs() }

    private fun name(d: LocalDate = LocalDate.now(Repo.zone())) = "mission-backup-$d-${System.currentTimeMillis()}.json"

    fun snapshotText(): String {
        val payload = Store.exportAll()
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return JSONObject()
            .put("format", "mission-backup-v1")
            .put("payload", payload)
            .put("sha256", sha)
            .toString()
    }

    fun auto(c: Context) {
        if (!Store.b("backup_on", true)) return
        val today = LocalDate.now(Repo.zone()).toString()
        if (Store.s("backup_last_day") == today && System.currentTimeMillis() - Store.l("backup_last_ms") < 6 * 3600000L) return
        runNow(c)
    }

    /** Returns a short status line. */
    fun runNow(c: Context): String {
        return try {
            val text = snapshotText()
            val backupName = name()
            File(dir(c), backupName).writeText(text)
            pruneLocal(dir(c))
            var where = "device"
            val file = Store.s("backup_file")
            if (file.isNotBlank()) {
                if (writeFile(
                        c,
                        Uri.parse(file),
                        text,
                    )
                ) {
                    where = "device + Drive file"
                } else {
                    Store.put("backup_err", "Backup file write failed")
                }
            }
            val tree = Store.s("backup_tree")
            if (tree.isNotBlank()) {
                if (writeToTree(
                        c,
                        Uri.parse(tree),
                        backupName,
                        text,
                    )
                ) {
                    where = "device + chosen folder"
                } else {
                    Store.put("backup_err", "Folder write failed")
                }
            }
            Store.put("backup_last_day", LocalDate.now(Repo.zone()).toString())
            Store.put("backup_last_ms", System.currentTimeMillis())
            Store.put("backup_where", where)
            try {
                android.app.backup.BackupManager.dataChanged(c.packageName)
            } catch (e: Exception) {
            }
            "Backup saved to $where"
        } catch (e: Exception) {
            Log.e("backup", e)
            "Backup failed: ${e.message}"
        }
    }

    fun writeFile(
        c: Context,
        u: Uri,
        text: String,
    ): Boolean =
        try {
            c.contentResolver.openOutputStream(u, "wt")?.use { it.write(text.toByteArray()) } != null
        } catch (e: Exception) {
            Log.e("backup-file", e)
            false
        }

    private fun pruneLocal(dir: File) {
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("mission-backup-") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(7)
            ?.forEach { it.delete() }
    }

    fun setFile(
        c: Context,
        uri: Uri,
    ) {
        try {
            c.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
        }
        Store.put("backup_file", uri.toString())
    }

    fun writeToTree(
        c: Context,
        tree: Uri,
        fname: String,
        text: String,
    ): Boolean {
        return try {
            val treeDoc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            var doc: Uri? = null
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            c.contentResolver.query(
                kids,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use {
                    cur ->
                while (cur.moveToNext() && doc == null) {
                    if (cur.getString(1) == fname) {
                        doc = DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0))
                    }
                }
            }
            val target = doc ?: DocumentsContract.createDocument(c.contentResolver, treeDoc, "application/json", fname) ?: return false
            c.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) } ?: return false
            true
        } catch (e: Exception) {
            Log.e("backup-tree", e)
            false
        }
    }

    fun setTree(
        c: Context,
        uri: Uri,
    ) {
        try {
            c.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (
            e: Exception,
        ) {
        }
        Store.put("backup_tree", uri.toString())
    }

    fun export(
        c: Context,
        uri: Uri,
    ): Boolean =
        try {
            val output = c.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Could not open backup destination")
            output.use { it.write(snapshotText().toByteArray()) }
            true
        } catch (e: Exception) {
            Log.e("export", e)
            false
        }

    fun import(
        c: Context,
        uri: Uri,
        merge: Boolean,
    ): Int =
        try {
            val t = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val parsed = JSONObject(t)
            val payload = if (parsed.optString("format") == "mission-backup-v1") {
                val data = parsed.optJSONObject("payload") ?: throw IllegalArgumentException("Missing backup payload")
                val sha = MessageDigest.getInstance("SHA-256")
                    .digest(data.toString().toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(sha.toByteArray(), parsed.optString("sha256").toByteArray())) {
                    throw IllegalArgumentException("Backup checksum mismatch")
                }
                data
            } else {
                parsed
            }
            Zite.invalidate()
            Store.importAll(payload, merge)
        } catch (e: Exception) {
            Log.e("import", e)
            -1
        }

    fun localFiles(c: Context): List<File> = dir(c).listFiles()?.sortedByDescending { it.name } ?: emptyList()

    fun restoreLocal(
        c: Context,
        f: File,
    ): Int =
        try {
            Store.importAll(JSONObject(f.readText()), false)
        } catch (e: Exception) {
            -1
        }
}
