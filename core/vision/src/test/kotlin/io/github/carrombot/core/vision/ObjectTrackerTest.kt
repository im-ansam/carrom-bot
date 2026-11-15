package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackerTest {
    private val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(400.0, 400.0))
    private val config = TrackingConfig(30.0, 0.5, 2.0, 30.0, 200_000_000L, 500_000_000L)

    @Test
    fun identitySurvivesBriefOcclusionWithoutPublishingMissingGeometry() {
        val tracker = ObjectTracker(bounds, config)
        val first = tracker.update(frame(disc(100.0)), 0L)
        val missing = tracker.update(frame(), 100_000_000L)
        assertTrue(missing.objects.isEmpty())
        assertTrue(missing.hasMissingObjects)
        val recovered = tracker.update(frame(disc(110.0)), 200_000_000L)
        assertEquals(first.objects.single().id, recovered.objects.single().id)
        assertFalse(recovered.objects.single().stationary)
        assertEquals(
            110.0,
            recovered.objects
                .single()
                .circle.center.x,
            0.0,
        )
    }

    @Test
    fun smoothingDoesNotHideMotionAndVelocityUsesSeconds() {
        val tracker = ObjectTracker(bounds, config)
        tracker.update(frame(disc(100.0)), 0L)
        val moving = tracker.update(frame(disc(120.0)), 100_000_000L).objects.single()
        assertEquals(110.0, moving.circle.center.x, 0.0)
        assertEquals(200.0, moving.velocity.x, 0.0001)
        assertFalse(moving.stationary)
    }

    @Test
    fun staleOrOutOfOrderFramesCannotAdvanceStateAndLongGapExpiresIdentity() {
        val tracker = ObjectTracker(bounds, config)
        val id =
            tracker
                .update(frame(disc(100.0)), 100L)
                .objects
                .single()
                .id
        assertEquals(TrackingFailure.OUT_OF_ORDER, tracker.update(frame(disc(101.0)), 100L).failure)
        assertEquals(TrackingFailure.STALE_FRAME, tracker.update(frame(disc(101.0)), 200L, 600_000_201L).failure)
        val fresh = tracker.update(frame(disc(100.0)), 600_000_300L)
        assertNotEquals(id, fresh.objects.single().id)
        assertFalse(fresh.objects.single().stationary)
    }

    @Test
    fun ambiguousAssociationsDoNotGuessIdentity() {
        val tracker = ObjectTracker(bounds, config)
        tracker.update(frame(disc(100.0), disc(140.0)), 0L)
        val ambiguous = tracker.update(frame(disc(120.0)), 100_000_000L)
        assertTrue(ambiguous.objects.isEmpty())
        assertTrue(ambiguous.hasMissingObjects)
    }

    @Test
    fun stationaryRequiresDwellAndRawPositionsRemainWithinAnchorTolerance() {
        val tracker = ObjectTracker(bounds, config)
        tracker.update(frame(disc(100.0)), 0L)
        tracker.update(frame(disc(101.0)), 100_000_000L)
        assertTrue(
            tracker
                .update(frame(disc(100.5)), 200_000_000L)
                .objects
                .single()
                .stationary,
        )
        assertFalse(
            tracker
                .update(frame(disc(103.0)), 300_000_000L)
                .objects
                .single()
                .stationary,
        )
    }

    @Test
    fun estimatorOnlyExposesFreshCompleteQualifiedStationaryBoard() {
        val inventory = mapOf(ObjectKind.STRIKER to 1, ObjectKind.BLACK_DISC to 1)
        val estimator = BoardStateEstimator(bounds, config, inventory, 0.9)

        fun observe(
            time: Long,
            vararg detections: Detection,
        ): BoardEstimate = estimator.update(frame(*detections), time)
        val striker = Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.95)
        assertTrue(observe(0L, striker, disc(100.0)) is BoardEstimate.Unavailable)
        observe(100_000_000L, striker, disc(100.0))
        val ready = observe(200_000_000L, striker, disc(100.0)) as BoardEstimate.Ready
        assertEquals(0.95, ready.confidence, 0.0)
        assertEquals(
            Vec2(0.0, 0.0),
            ready.board.discs
                .single()
                .velocity,
        )
        assertTrue(observe(300_000_000L, striker) is BoardEstimate.Unavailable)
        assertTrue(observe(400_000_000L, striker, disc(100.0)) is BoardEstimate.Unavailable)
        val stale = estimator.update(frame(striker, disc(100.0)), 500_000_000L, 1_100_000_000L)
        assertTrue(stale is BoardEstimate.Unavailable)
    }

    @Test
    fun lowConfidenceFailuresAndUnrepresentableObstaclesNeverYieldReady() {
        val estimator = BoardStateEstimator(bounds, config, mapOf(ObjectKind.STRIKER to 1), 0.9)
        val low = Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.5)
        repeat(4) { assertTrue(estimator.update(frame(low), it * 100_000_000L) is BoardEstimate.Unavailable) }
        val failed = estimator.update(ObjectDetection.Failed(ObjectDetectionFailure.INVALID_FRAME), 400_000_000L)
        assertTrue(failed is BoardEstimate.Unavailable)
        assertThrows(IllegalArgumentException::class.java) {
            BoardStateEstimator(bounds, config, mapOf(ObjectKind.STRIKER to 1, ObjectKind.OBSTACLE to 1), 0.9)
        }
    }

    @Test
    fun confidenceMustRemainQualifiedThroughoutStationaryDwell() {
        val estimator = BoardStateEstimator(bounds, config, mapOf(ObjectKind.STRIKER to 1), 0.9)
        val striker = Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.5)
        repeat(3) { estimator.update(frame(striker), it * 100_000_000L) }
        assertTrue(estimator.update(frame(striker.copy(confidence = 0.95)), 300_000_000L) is BoardEstimate.Unavailable)
    }

    @Test
    fun failedObservationBreaksDwellButRetainsIdentity() {
        val tracker = ObjectTracker(bounds, config)
        val id =
            tracker
                .update(frame(disc(100.0)), 0L)
                .objects
                .single()
                .id
        tracker.update(frame(disc(100.0)), 100_000_000L)
        tracker.update(ObjectDetection.Failed(ObjectDetectionFailure.OPENCV_ERROR), 200_000_000L)
        val recovered = tracker.update(frame(disc(100.0)), 300_000_000L).objects.single()
        assertEquals(id, recovered.id)
        assertFalse(recovered.stationary)
    }

    @Test
    fun radiusCreepCannotAccumulateIntoStationaryGeometry() {
        val tracker = ObjectTracker(bounds, config)
        tracker.update(frame(disc(100.0)), 0L)
        tracker.update(frame(disc(100.0).copy(circle = Circle(Vec2(100.0, 100.0), 6.5))), 100_000_000L)
        val grown = tracker.update(frame(disc(100.0).copy(circle = Circle(Vec2(100.0, 100.0), 8.0))), 200_000_000L)
        assertFalse(grown.objects.single().stationary)
    }

    @Test
    fun completeInventoryMapsAllSharedKindsAndOwnsItsInputs() {
        val inventory =
            mutableMapOf(
                ObjectKind.POCKET to 1,
                ObjectKind.STRIKER to 1,
                ObjectKind.BLACK_DISC to 1,
                ObjectKind.WHITE_DISC to 1,
                ObjectKind.QUEEN to 1,
            )
        val estimator = BoardStateEstimator(bounds, config, inventory, 0.9)
        inventory.clear()
        val objects =
            listOf(
                Detection(ObjectKind.POCKET, Circle(Vec2(0.0, 0.0), 12.0), 0.95),
                Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.95),
                disc(100.0),
                disc(150.0).copy(kind = ObjectKind.WHITE_DISC),
                disc(200.0).copy(kind = ObjectKind.QUEEN),
            )
        estimator.update(ObjectDetection.Observed(objects), 0L)
        estimator.update(ObjectDetection.Observed(objects.reversed()), 100_000_000L)
        val ready = estimator.update(ObjectDetection.Observed(objects), 200_000_000L) as BoardEstimate.Ready
        assertEquals(listOf("BLACK", "WHITE", "QUEEN"), ready.board.discs.map { it.kind.name })
        assertEquals(1, ready.board.pockets.size)
        val ids = ready.board.discs.map { it.id } + ready.board.pockets.map { it.id } + ready.board.striker.id
        assertEquals(5, ids.distinct().size)
        assertThrows(UnsupportedOperationException::class.java) { (ready.board.discs as MutableList).clear() }
    }

    @Test
    fun typeChangesAndExpiredMissingObjectsNeverInventCompleteInventory() {
        val inventory = mapOf(ObjectKind.STRIKER to 1, ObjectKind.BLACK_DISC to 1)
        val estimator = BoardStateEstimator(bounds, config, inventory, 0.9)
        val striker = Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.95)
        estimator.update(frame(striker, disc(100.0)), 0L)
        val changed = estimator.update(frame(striker, disc(100.0).copy(kind = ObjectKind.WHITE_DISC)), 100_000_000L)
        assertTrue(changed is BoardEstimate.Unavailable)
        repeat(3) {
            assertTrue(estimator.update(frame(striker), 700_000_000L + it * 100_000_000L) is BoardEstimate.Unavailable)
        }
    }

    @Test
    fun invalidInputsAndConfigurationFailExplicitly() {
        val tracker = ObjectTracker(bounds, config)
        assertEquals(TrackingFailure.INVALID_OBSERVATION, tracker.update(frame(disc(1.0)), 0L).failure)
        assertEquals(TrackingFailure.INVALID_OBSERVATION, tracker.update(frame(disc(100.0), disc(100.0)), 1L).failure)
        val invalid = tracker.update(frame(disc(100.0).copy(confidence = Double.NaN)), 2L)
        assertEquals(TrackingFailure.INVALID_OBSERVATION, invalid.failure)
        assertThrows(IllegalArgumentException::class.java) { tracker.update(frame(), -1L) }
        assertThrows(IllegalArgumentException::class.java) { tracker.update(frame(), 4L, 3L) }
        listOf(
            { config.copy(associationDistanceMm = Double.NaN) },
            { config.copy(smoothingAlpha = 0.0) },
            { config.copy(stationaryRadiusMm = -1.0) },
            { config.copy(stationarySpeedMmPerSecond = Double.POSITIVE_INFINITY) },
            { config.copy(stationaryDurationNanos = 0L) },
            { config.copy(maxGapNanos = 0L) },
            { BoardStateEstimator(bounds, config, emptyMap(), 0.9) },
            { BoardStateEstimator(bounds, config, mapOf(ObjectKind.STRIKER to 1), Double.NaN) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun nonfiniteVelocityFailsRatherThanThrowingOrPublishingPlausibleMotion() {
        val huge = BoardBounds(Vec2(0.0, 0.0), Vec2(1e300, 1e300))
        val tracker = ObjectTracker(huge, config.copy(associationDistanceMm = 1e300))

        fun detection(x: Double) = Detection(ObjectKind.BLACK_DISC, Circle(Vec2(x, 1e290), 1.0), 0.95)
        tracker.update(frame(detection(1e290)), 0L)
        assertEquals(TrackingFailure.INVALID_OBSERVATION, tracker.update(frame(detection(5e299)), 1L).failure)
    }

    @Test
    fun invalidConfidenceCannotDisappearThroughEstimatorFiltering() {
        val estimator = BoardStateEstimator(bounds, config, mapOf(ObjectKind.STRIKER to 1), 0.9)
        val striker = Detection(ObjectKind.STRIKER, Circle(Vec2(200.0, 300.0), 10.0), 0.95)
        repeat(3) { estimator.update(frame(striker), it * 100_000_000L) }
        val invalid = estimator.update(frame(striker, disc(100.0).copy(confidence = Double.NaN)), 300_000_000L)
        assertEquals(BoardEstimate.Unavailable(BoardUnavailableReason.INVALID_FRAME), invalid)
    }

    @Test
    fun laggingSmoothedGeometryCannotQualifyAsStationaryPlannerInput() {
        val tracker = ObjectTracker(bounds, config.copy(smoothingAlpha = 0.01))
        tracker.update(frame(disc(100.0)), 0L)
        tracker.update(frame(disc(120.0)), 100_000_000L)
        tracker.update(frame(disc(120.0)), 200_000_000L)
        val lagging = tracker.update(frame(disc(120.0)), 300_000_000L).objects.single()
        assertTrue(lagging.circle.center.x < 102.0)
        assertFalse(lagging.stationary)
    }

    private fun disc(x: Double): Detection = Detection(ObjectKind.BLACK_DISC, Circle(Vec2(x, 100.0), 5.0), 0.95)

    private fun frame(vararg objects: Detection): ObjectDetection = ObjectDetection.Observed(objects.toList())
}
