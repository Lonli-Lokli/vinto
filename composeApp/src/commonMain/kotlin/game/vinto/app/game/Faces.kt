package game.vinto.app.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import game.vinto.app.theme.AvatarGrounds
import game.vinto.app.theme.GeneratedAvatar
import game.vinto.protocol.AvatarTraits
import game.vinto.protocol.PlayerProfile
import game.vinto.protocol.avatarKindOf
import game.vinto.protocol.mintAvatar

/**
 * The faces the seats at this table chose, by name.
 *
 * A composition local for the same reason `LocalPacing` and `LocalCounting` are: it is a thing
 * every screen touches and no screen owns. Five places draw a seat — the felt's plate, the rail's
 * seat buttons, the standings, the coalition board and the Ace's picker — and every one of them
 * has a *name* and nothing else. Threading a seat index down five call chains to key this would
 * be a bigger change than the one that made the name a safe key, which was worth making anyway:
 * `uniqueNickname` stops two people at one table both being Lucky Rowan.
 *
 * **Empty in a solo game, and that is the whole fallback.** Nobody picks a face offline — the
 * three opponents are the four elements and wear their own emblems — so an empty map means every
 * seat falls through to `portraitFor`, exactly as before any of this existed. Online it is filled
 * from the lobby the room broadcasts, which is why a seat that joined an older room still draws.
 */
val LocalFaces = compositionLocalOf<Map<String, PlayerProfile>> { emptyMap() }

/**
 * The chosen face for [name], or null when this seat has none and the emblem should be drawn.
 *
 * Null rather than a default face on purpose. A bot *has* a portrait — Ember is fire and always
 * has been — and handing it a generated mark instead would replace something meaningful with
 * something arbitrary. The same is true of a player on a build older than protocol 3, whose
 * profile carries no face at all: the emblem their name picks is a better answer than a mark
 * they never chose.
 */
@Composable
@ReadOnlyComposable
fun chosenFace(name: String): PlayerProfile? = LocalFaces.current[name]

/** The traits that profile draws, ready for [GeneratedAvatar]. */
fun PlayerProfile.traits(): AvatarTraits = mintAvatar(avatarKindOf(avatarKind), avatarSeed)

/**
 * The ground it sits on, wrapped into the palette.
 *
 * Wrapped rather than bounds-checked: the palette can grow, and an index from a newer build than
 * this one must land on *a* colour rather than on an exception. Every colour in it is measured by
 * `ContrastTest`, so wherever it lands the ink on top of it is still readable.
 */
fun PlayerProfile.ground(): Color = AvatarGrounds[avatarGround.mod(AvatarGrounds.size)]
