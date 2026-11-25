package app.cue.launcher.system.safe

import android.content.Context
import app.cue.launcher.engine.safe.VaultSource
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

// The phone's own storage could not take the copy. Writing a private file fails in practice only
// when the storage is full.
class SafeNoSpace(cause: IOException) : Exception(cause)

// The session's snapshot of the vault file: its ciphertext in private storage that is never backed
// up, so the session reads one consistent file at any position, however the provider serves the
// original and whatever replaces it meanwhile. Its name goes as soon as the space is reserved: only
// the open descriptor keeps the file, so closing it or the end of the process frees it.
class SafeCopies(private val directory: File, private val reserve: (FileDescriptor, Long) -> Unit) {

    // head is what the caller already read of the input. A known length is reserved before anything
    // is copied, so a vault that does not fit fails at once rather than after a long download.
    fun copy(head: ByteArray, input: InputStream, lengthHint: Long, onChunk: () -> Unit): SafeCopy {
        val file = File(directory, NAME)
        val access = ownStorage {
            directory.mkdirs()
            RandomAccessFile(file, "rw")
        }
        var handed = false
        try {
            if (lengthHint > 0) ownStorage { reserve(access.fd, lengthHint) }
            file.delete()
            ownStorage { access.write(head) }
            var length = head.size.toLong()
            val buffer = ByteArray(CHUNK)
            while (true) {
                onChunk()
                val read = input.read(buffer)
                if (read < 0) break
                ownStorage { access.write(buffer, 0, read) }
                length += read
            }
            ownStorage { access.setLength(length) }
            return SafeCopy(access, length).also { handed = true }
        } finally {
            if (!handed) {
                access.close()
                file.delete()
            }
        }
    }

    private inline fun <T> ownStorage(step: () -> T): T = try {
        step()
    } catch (failure: IOException) {
        throw SafeNoSpace(failure)
    }

    private companion object {
        const val NAME = "container.cuevault"
        const val CHUNK = 256 * 1024
    }
}

class SafeCopy internal constructor(private val access: RandomAccessFile, val length: Long) : VaultSource {

    private val channel = access.channel

    // Positional reads, which a FileChannel serves on any thread without moving a shared position.
    override fun read(position: Long, buffer: ByteArray, length: Int) {
        val target = ByteBuffer.wrap(buffer, 0, length)
        while (target.hasRemaining()) {
            if (channel.read(target, position + target.position()) < 0) throw EOFException("the copy ends early")
        }
    }

    override fun close() = access.close()
}

internal fun safeCopyDirectory(context: Context): File = File(context.noBackupFilesDir, "safe")

// A copy outlives its process only when the process died between creating the file and removing its
// name, and a new process has no session, so whatever is there is a leftover.
fun discardSafeCopies(context: Context) {
    safeCopyDirectory(context).deleteRecursively()
}
