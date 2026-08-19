package me.ash.reader.infrastructure.sync

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class ReadStatusOutboxSnapshot(
    val localDiffs: Map<String, Diff> = emptyMap(),
    val pendingRemoteDiffs: Map<String, Diff> = emptyMap(),
)

internal class ReadStatusOutboxStore(
    private val directory: File,
    private val gson: Gson = Gson(),
) {
    private val stateFile = directory.resolve(STATE_FILE_NAME)
    private val temporaryFile = directory.resolve("$STATE_FILE_NAME.tmp")

    fun read(): ReadStatusOutboxSnapshot {
        if (!stateFile.isFile) return ReadStatusOutboxSnapshot()
        val root = JsonParser.parseString(stateFile.readText())
        if (root.isJsonObject && root.asJsonObject.has("localDiffs")) {
            return gson.fromJson(root, ReadStatusOutboxSnapshot::class.java)
                ?: ReadStatusOutboxSnapshot()
        }

        // Versions before the outbox stored only local, uncommitted diffs as a bare map.
        val legacyType = object : TypeToken<Map<String, Diff>>() {}.type
        val legacyDiffs = gson.fromJson<Map<String, Diff>>(root, legacyType).orEmpty()
        return ReadStatusOutboxSnapshot(localDiffs = legacyDiffs)
    }

    fun write(snapshot: ReadStatusOutboxSnapshot) {
        if (snapshot.localDiffs.isEmpty() && snapshot.pendingRemoteDiffs.isEmpty()) {
            delete()
            return
        }
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create read-status outbox directory"
        }
        FileOutputStream(temporaryFile).use { output ->
            val writer = output.bufferedWriter()
            gson.toJson(snapshot, writer)
            writer.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temporaryFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                stateFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun delete() {
        Files.deleteIfExists(temporaryFile.toPath())
        Files.deleteIfExists(stateFile.toPath())
    }

    private companion object {
        const val STATE_FILE_NAME = "read_status_outbox.json"
    }
}
