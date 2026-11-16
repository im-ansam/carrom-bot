package io.github.carrombot.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Bounded scrollable, accessible advisory controls. All calls belong to the main looper.
 * Callbacks express intent to the owner; this view owns no capture, planner or input injector.
 * Start/stop/pause affect aiming only. End sharing is a distinct, explicit resource-stop action.
 */
@SuppressLint("ViewConstructor")
public class FloatingControls(
    context: Context,
    private val onAction: (AimAction) -> Unit,
    onEndSharing: () -> Unit,
) : ScrollView(context) {
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status =
        TextView(context).apply {
            setTextColor(Color.BLACK)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
    private val start = button(R.string.start_aiming) { onAction(AimAction.Start) }
    private val stop = button(R.string.stop_aiming) { onAction(AimAction.Stop) }
    private val pause = button(R.string.pause_aiming) { onAction(AimAction.Pause) }
    private val resume = button(R.string.resume_aiming) { onAction(AimAction.Resume) }
    private val power = TextView(context).apply { setTextColor(Color.BLACK) }
    private val next = button(R.string.next_candidate) { onAction(AimAction.Next) }
    private val candidates = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(Color.WHITE)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(status)
        content.addView(start)
        content.addView(stop)
        content.addView(pause)
        content.addView(resume)
        content.addView(power)
        content.addView(next)
        content.addView(candidates)
        content.addView(button(R.string.end_sharing, onEndSharing))
        render(AimControlsState())
    }

    /** Selection and recommended power always come from the same immutable scene. */
    public fun render(state: AimControlsState) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        status.text = state.message ?: context.getString(statusLabel(state))
        start.visibility = visible(state.phase == AimPhase.STOPPED || state.phase == AimPhase.ERROR)
        stop.visibility = visible(state.phase != AimPhase.STOPPED)
        pause.visibility = visible(state.phase == AimPhase.RUNNING)
        resume.visibility = visible(state.phase == AimPhase.PAUSED)
        power.text = state.selected?.let {
            context.getString(R.string.recommended_power, (it.power * PERCENT).roundToInt())
        } ?: context.getString(R.string.power_unavailable)
        next.isEnabled = (state.scene?.candidates?.size ?: 0) > 1
        val proposals = state.scene?.candidates.orEmpty()
        val sameIds =
            candidates.childCount == proposals.size &&
                proposals.indices.all {
                    candidates.getChildAt(it).tag == proposals[it].id
                }
        if (!sameIds) {
            candidates.removeAllViews()
            proposals.forEach { candidate ->
                candidates.addView(
                    button(R.string.select_candidate) { onAction(AimAction.Select(candidate.id)) }.apply {
                        tag = candidate.id
                    },
                )
            }
        }
        proposals.forEachIndexed { index, candidate ->
            updateCandidate(candidates.getChildAt(index) as Button, index, candidate, state.scene?.selectedId)
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val configuration = resources.configuration
        val width = dp(minOf(MAX_WIDTH_DP, configuration.screenWidthDp - MARGIN_DP))
        val height = dp(minOf(MAX_HEIGHT_DP, configuration.screenHeightDp / 2))
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(heightMeasureSpec), height), MeasureSpec.AT_MOST),
        )
    }

    private fun updateCandidate(
        button: Button,
        index: Int,
        candidate: RenderedCandidate,
        selectedId: String?,
    ) {
        button.isSelected = candidate.id == selectedId
        button.text =
            context.getString(
                if (button.isSelected) R.string.candidate_selected_label else R.string.candidate_label,
                index + 1,
                candidate.id,
            )
        button.contentDescription =
            context.getString(
                if (button.isSelected) R.string.candidate_selected else R.string.candidate_select_description,
                index + 1,
                candidate.id,
                (candidate.power * PERCENT).roundToInt(),
            )
    }

    private fun statusLabel(state: AimControlsState): Int =
        when (state.phase) {
            AimPhase.STOPPED -> {
                R.string.aiming_stopped
            }

            AimPhase.PAUSED -> {
                R.string.aiming_paused
            }

            AimPhase.ERROR -> {
                R.string.aiming_failed
            }

            AimPhase.RUNNING -> {
                when {
                    state.scene == null -> R.string.aiming_waiting
                    state.scene.candidates.isEmpty() -> R.string.aiming_no_candidates
                    else -> R.string.aiming_advisory
                }
            }
        }

    private fun button(
        label: Int,
        action: () -> Unit,
    ): Button =
        Button(context).apply {
            setText(label)
            isAllCaps = false
            minimumHeight = dp(MINIMUM_TOUCH_DP)
            minimumWidth = minimumHeight
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun visible(value: Boolean): Int = if (value) View.VISIBLE else View.GONE

    private companion object {
        const val MINIMUM_TOUCH_DP = 48
        const val MAX_WIDTH_DP = 240
        const val MAX_HEIGHT_DP = 320
        const val MARGIN_DP = 32
        const val PERCENT = 100
    }
}
