package game.vinto.app

import game.vinto.client.Vault
import java.io.File

/** A file beside the user's home directory. The JVM target exists for tests; this suits it. */
actual fun platformVault(): Vault = FileVault(File(System.getProperty("java.io.tmpdir"), "vinto"))

private class FileVault(private val directory: File) : Vault {
    override fun read(key: String): String? = File(directory, key).takeIf { it.isFile }?.readText()

    override fun write(key: String, value: String) {
        directory.mkdirs()
        File(directory, key).writeText(value)
    }

    override fun erase(key: String) {
        File(directory, key).delete()
    }
}
