package game.vinto.app

import game.vinto.client.MemoryVault
import game.vinto.client.identity
import game.vinto.protocol.AvatarKind
import game.vinto.protocol.AvatarTraits
import game.vinto.protocol.looksMinted
import game.vinto.protocol.mintAvatar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lobby the store shows is somebody a player could be, smiling.
 *
 * Its face used to be whichever one the capture's seed minted, and that one frowned — the last
 * thing the App Store preview showed before its end card. The scene stages its identity instead,
 * and these hold the two things that could quietly go wrong with a pinned choice: a name the room
 * would refuse at its door, and a seed that no longer draws the face it was chosen for.
 */
class TheLobbyIsStagedTest {

    @Test
    fun theStagedNameIsOneTheRoomWouldAccept() {
        val vault = MemoryVault().also(::stageTheLobby)
        assertTrue(looksMinted(vault.identity { 0L }.nickname), "the lobby's name could not have been minted")
    }

    @Test
    fun theStagedFaceSmiles() {
        val vault = MemoryVault().also(::stageTheLobby)
        val saved = vault.identity { 0L }
        assertEquals(AvatarKind.FACE.ordinal, saved.avatarKind)

        val face = mintAvatar(AvatarKind.FACE, saved.avatarSeed) as AvatarTraits.Face
        assertEquals(SMILE, face.mouth, "the lobby's face is not smiling: $face")
        assertEquals(0, face.brow, "the lobby's face is not level-browed: $face")
    }

    private companion object {
        /** A mouth of 2 is the smile; 3 is an open "O". */
        const val SMILE = 2
    }
}
