package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A browser that refuses storage still gets a game, and loses only what it refused to keep.
 *
 * On the live site with `localStorage` refusing reads, the page stayed on "Dealing…": the first
 * thing the app does is read the vault, and the read threw. The refusal itself cannot happen on
 * this side of the browser, so the vault here refuses the way `localStorage` does — by throwing.
 */
class AVaultThatRefusesStillAnswersTest {

    private class Refusing(private val reads: Boolean, private val writes: Boolean) : Vault {
        val kept = MemoryVault()
        override fun read(key: String): String? = if (reads) error("The operation is insecure.") else kept.read(key)
        override fun write(key: String, value: String) =
            if (writes) error("The quota has been exceeded.") else kept.write(key, value)
        override fun erase(key: String) = if (writes) error("The quota has been exceeded.") else kept.erase(key)
    }

    @Test
    fun aVaultThatCannotBeReadReadsAsAFirstLaunch() {
        assertNull(ForgivingVault(Refusing(reads = true, writes = true)).read("vinto.settings"))
    }

    @Test
    fun whatStorageWouldNotTakeIsStillThereForTheSession() {
        val vault = ForgivingVault(Refusing(reads = true, writes = true))

        vault.write("vinto.settings", "sound=off")
        assertEquals("sound=off", vault.read("vinto.settings"), "a setting changed and changed back on the next read")

        vault.erase("vinto.settings")
        assertNull(vault.read("vinto.settings"))
    }

    /** A full quota refuses the write and still reads: the new value wins, not the stored one. */
    @Test
    fun aRefusedWriteIsNotOverruledByWhatWasStoredBefore() {
        val storage = Refusing(reads = false, writes = true)
        storage.kept.write("vinto.settings", "sound=on")
        val vault = ForgivingVault(storage)

        vault.write("vinto.settings", "sound=off")
        assertEquals("sound=off", vault.read("vinto.settings"))
    }

    @Test
    fun storageThatWorksIsUsed() {
        val storage = Refusing(reads = false, writes = false)
        ForgivingVault(storage).write("vinto.settings", "sound=off")

        assertEquals("sound=off", storage.kept.read("vinto.settings"))
    }
}
