package io.github.carrombot.core.overlay

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun controlsExposeReversibleIntentAndKeepSharingStopSeparate() =
        main { activity ->
            val actions = mutableListOf<AimAction>()
            var ended = false
            val view = FloatingControls(activity, actions::add) { ended = true }
            activity.setContentView(view)
            button(view, "Start aiming").performClick()
            assertEquals(listOf(AimAction.Start), actions)
            view.render(AimControlsState(AimPhase.RUNNING))
            assertFalse(button(view, "Next best shot").isEnabled)
            assertTrue(texts(view).any { it.text.toString().contains("No aiming data") })
            button(view, "Pause aiming").performClick()
            view.render(AimControlsState(AimPhase.PAUSED))
            button(view, "Resume aiming").performClick()
            view.render(AimControlsState(AimPhase.RUNNING))
            button(view, "Stop aiming").performClick()
            assertEquals(listOf(AimAction.Start, AimAction.Pause, AimAction.Resume, AimAction.Stop), actions)
            assertFalse(ended)
            button(view, "End screen sharing").performClick()
            assertTrue(ended)
            view.render(AimControlsState(AimPhase.ERROR))
            assertTrue(button(view, "Start aiming").visibility == View.VISIBLE)
            assertTrue(texts(view).any { it.text.toString().contains("Aiming failed") })
        }

    @Test
    fun candidateAccessibilityAndPowerTrackSelectionWithoutReplacingFocusedControls() =
        main { activity ->
            val actions = mutableListOf<AimAction>()
            val view = FloatingControls(activity, actions::add) {}
            activity.setContentView(view)
            val first = candidate("bank", 0.35)
            val second = candidate("cut", 0.75)
            val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0))
            view.render(AimControlsState(AimPhase.RUNNING, OverlayScene(bounds, listOf(first, second), "bank")))
            val cut = button(view, "2: cut")
            assertEquals("Select candidate 2, cut, recommended power 75%", cut.contentDescription)
            cut.performClick()
            assertEquals(AimAction.Select("cut"), actions.last())
            button(view, "Next best shot").performClick()
            assertEquals(AimAction.Next, actions.last())
            view.render(AimControlsState(AimPhase.RUNNING, OverlayScene(bounds, listOf(first, second), "cut")))
            assertSame(cut, button(view, "Selected 2: cut"))
            assertTrue(cut.isSelected)
            assertEquals("Selected candidate 2, cut, recommended power 75%", cut.contentDescription)
            assertTrue(texts(view).any { it.text.toString().contains("Recommended power: 75%") })
            val node = cut.createAccessibilityNodeInfo()
            assertTrue(node.isClickable)
            assertTrue(node.isSelected)
            assertEquals(Button::class.java.name, node.className)
            assertTrue(cut.minimumHeight >= 48 * view.resources.displayMetrics.density)
            view.render(AimControlsState(AimPhase.PAUSED))
            assertTrue(texts(view).any { it.text.toString() == "Recommended power unavailable." })
            assertFalse(button(view, "Next best shot").isEnabled)
            assertTrue(texts(view).none { it.text.toString().contains("cut") })
        }

    @Test
    fun largeTextAndManyCandidatesStayScrollableWithinBoundedWindow() =
        main { activity ->
            val configuration = android.content.res.Configuration(instrumentation.context.resources.configuration)
            configuration.fontScale = 2f
            val context = instrumentation.context.createConfigurationContext(configuration)
            val view = FloatingControls(context, {}) {}
            activity.setContentView(view)
            val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0))
            val scene = OverlayScene(bounds, (1..20).map { candidate("shot $it", 0.5) }, "shot 1")
            view.render(AimControlsState(AimPhase.RUNNING, scene))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            assertTrue(view.height <= 320 * context.resources.displayMetrics.density)
            assertTrue(view.getChildAt(0).height > view.height)
            view.isSmoothScrollingEnabled = false
            view.fullScroll(View.FOCUS_DOWN)
            assertTrue(view.scrollY > 0)
        }

    private fun candidate(
        id: String,
        power: Double,
    ): RenderedCandidate =
        RenderedCandidate(
            id,
            listOf(BodyTrajectory("striker", true, listOf(Vec2(10.0, 10.0), Vec2(20.0, 20.0)))),
            emptyList(),
            power,
            null,
        )

    private fun texts(view: View): List<TextView> =
        (if (view is TextView) listOf(view) else emptyList()) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    private fun button(
        view: View,
        text: String,
    ): Button = texts(view).filterIsInstance<Button>().single { it.text.toString() == text }

    private fun main(action: (android.app.Activity) -> Unit) {
        ActivityScenario.launch(TouchTestActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }
}
