package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import game.vinto.app.game.Chrome
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGameSession
import game.vinto.client.Question
import game.vinto.client.Say
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.test.runTest

/**
 * The iPhone Duo as the suites draw it, measured on the simulator (Xcode 27.1, 2026-10-12): closed,
 * 466x678 points with a status column 84 wide down the right and its clock, connectivity and camera
 * in the column's top 150; open on its side, 951x669 with the same column and only a clock, in its top
 * 100; open upright, 669x951 with an 80-point strip across the top and the clock in its right-hand
 * corner. Each less the 20-point home indicator, which every screen keeps clear of.
 *
 * Half open, iOS reports the fold as a line about eight points wide: down the middle of the inner
 * screen held like a book, across it held like a laptop. And the closed phone turned on its side
 * (678x466) keeps its column down one edge with the camera at one end of it — not measured yet, so the
 * suites stand the camera at the column's foot on either edge, which is the case that can hide a pill.
 */
internal object Duos {
    const val CLOSED_W = 466
    const val CLOSED_H = 658
    const val OPEN_W = 951
    const val OPEN_H = 649
    const val UPRIGHT_W = 669
    const val UPRIGHT_H = 931
    const val TURNED_W = 678
    const val TURNED_H = 446
    const val COLUMN = 84
    const val STRIP = 80

    /** Where the closed column's camera ends: nothing of the table's may start above it. */
    const val CAMERA_FOOT = 150f

    /** The closed Duo's column, with the clock over the camera in it, in the table box's coordinates. */
    val CLOSED = Chrome(
        column = COLUMN.dp,
        occlusions = listOf(DpRect(390.dp, 10.dp, 454.dp, 50.dp), DpRect(386.dp, 60.dp, 458.dp, 150.dp)),
    )

    /** The open Duo's on its side: the clock alone. */
    val OPEN = Chrome(column = COLUMN.dp, occlusions = listOf(DpRect(880.dp, 20.dp, 940.dp, 100.dp)))

    /** The open Duo upright: the clock and the connectivity in the corner of the strip across its top. */
    val CLOCK_IN_THE_CORNER = DpRect(560.dp, 20.dp, 660.dp, 60.dp)
    val UPRIGHT = Chrome(
        strip = STRIP.dp,
        stripEnd = CLOCK_IN_THE_CORNER.left,
        occlusions = listOf(CLOCK_IN_THE_CORNER),
    )

    /** Half open like a book: the open column and its clock, and the fold standing up the middle. */
    val SPINE = DpRect(471.dp, 0.dp, 479.dp, OPEN_H.dp)
    val BOOK = OPEN.copy(division = SPINE)

    /** Half open like a laptop: upright, the strip and its clock, and the fold lying across the middle. */
    val HINGE_LINE = DpRect(0.dp, 466.dp, UPRIGHT_W.dp, 474.dp)
    val LAPTOP = UPRIGHT.copy(division = HINGE_LINE)

    /** Where the closed and the open column begin across the window. */
    val CLOSED_COLUMN_LEFT: Float = (CLOSED_W - COLUMN).toFloat()
    val OPEN_COLUMN_LEFT: Float = (OPEN_W - COLUMN).toFloat()

    private const val SEED = 8L

    /** The camera at the foot of a turned column: a block 60 across and 76 down, 8 clear of the foot. */
    private const val LENS_IN = 12
    private const val LENS_W = 60
    private const val LENS_TOP = 362
    private const val LENS_FOOT = 438

    /** And a clock at its top, for the column that has one at each end. */
    private const val CLOCK_TOP = 8
    private const val CLOCK_FOOT = 44

    fun closed(): TableLayout = TableLayout.forScreen(CLOSED_W.dp, CLOSED_H.dp, CLOSED)

    fun open(): TableLayout = TableLayout.forScreen(OPEN_W.dp, OPEN_H.dp, OPEN)

    fun book(): TableLayout = TableLayout.forScreen(OPEN_W.dp, OPEN_H.dp, BOOK)

    fun laptop(): TableLayout = TableLayout.forScreen(UPRIGHT_W.dp, UPRIGHT_H.dp, LAPTOP)

    fun turned(chrome: Chrome): TableLayout = TableLayout.forScreen(TURNED_W.dp, TURNED_H.dp, chrome)

    /**
     * The closed Duo turned on its side, its column down the right or the left edge with the camera at
     * the column's foot — and, with [clock], the clock at its top too.
     */
    fun turnedColumn(onRight: Boolean, clock: Boolean = false): Chrome {
        val from = if (onRight) TURNED_W - COLUMN else 0
        val left = (from + LENS_IN).dp
        val right = (from + LENS_IN + LENS_W).dp
        val camera = DpRect(left, LENS_TOP.dp, right, LENS_FOOT.dp)
        val time = DpRect(left, CLOCK_TOP.dp, right, CLOCK_FOOT.dp)
        return Chrome(
            column = COLUMN.dp,
            columnOnRight = onRight,
            occlusions = if (clock) listOf(time, camera) else listOf(camera),
        )
    }

    /** A plain card drawn — Swap or Discard — with the round's opening in the log. */
    fun drawn(): Pair<PlayerView, List<Say>> {
        lateinit var view: PlayerView
        lateinit var said: List<Say>
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            view = session.view.value
            said = session.log.value
        }
        return view to said
    }

    /** A King played and pointed at a card: the fourteen ranks to name, and "Put it down" beside them. */
    fun kingNaming(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
            val opponent = session.view.value.players.first { it.id != me }
            session.dispatch(
                GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, opponent.id, 0)),
            )
            view = session.view.value
        }
        return view
    }
}

/** Draws the table as [layout] lays it out, at the system text size [fontScale], with a way out. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showTable(
    view: PlayerView,
    said: List<Say>,
    layout: TableLayout,
    fontScale: Float = 1f,
    question: Question = Question.None,
) {
    setContent {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            VintoTheme {
                Box(modifier = Modifier.size(layout.width, layout.height)) {
                    TableScreen(
                        state = TableState(view, tableFor(view, question), null, said, 1),
                        layout = layout,
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onLeave = {},
                    )
                }
            }
        }
    }
    waitForIdle()
}

/** Every node a thing on screen is named by, and where it is. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.nodesNamed(name: String): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher("named $name") { it.calledAloud() == name }).fetchSemanticsNodes()

/** Where the one thing named [name] is. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.whereIs(name: String): Rect = nodesNamed(name).single().boundsInRoot

/** What a node is called aloud: its first description. */
internal fun SemanticsNode.calledAloud(): String =
    config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()
