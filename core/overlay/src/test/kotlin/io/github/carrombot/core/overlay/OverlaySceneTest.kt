package io.github.carrombot.core.overlay

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OverlaySceneTest {
    private val bounds = BoardBounds(Vec2(10.0, 20.0), Vec2(110.0, 220.0))

    @Test
    fun normalizedCoordinatesAndTranslatedBoardRoundTrip() {
        val mapping = BoardMapping(bounds, CoordinateTransform(bounds.min, Vec2(30.0, 40.0), 2.0))
        assertEquals(Vec2(130.0, 240.0), mapping.normalizedToScreen(Vec2(0.5, 0.5)))
        val point = Vec2(63.2, 127.8)
        val actual = mapping.toBoard(mapping.toScreen(point))
        assertEquals(point.x, actual.x, 1e-10)
        assertEquals(point.y, actual.y, 1e-10)
        assertThrows(IllegalArgumentException::class.java) { mapping.normalizedToScreen(Vec2(1.1, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            BoardMapping(bounds, CoordinateTransform(bounds.min, Vec2(0.0, 0.0), Double.MIN_VALUE))
        }
    }

    @Test
    fun ownsNestedPathsAndRejectsInvalidAnnotations() {
        val points = mutableListOf(Vec2(20.0, 30.0), Vec2(40.0, 50.0), Vec2(25.0, 100.0))
        val path = BodyTrajectory("striker", true, points)
        val paths = mutableListOf(path)
        val candidate = RenderedCandidate("A", paths, emptyList(), 0.6, null)
        points.clear()
        paths.clear()
        assertEquals(
            3,
            candidate.trajectories
                .single()
                .points.size,
        )
        assertThrows(IllegalArgumentException::class.java) { BodyTrajectory("disc", false, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            RenderedCandidate("A", listOf(path), emptyList(), 0.0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RenderedCandidate("A", listOf(path), emptyList(), 0.5, Double.NaN)
        }
    }

    @Test
    fun sceneRejectsUnknownSelectionDuplicateIdsAndOutOfBoardGeometry() {
        val path = BodyTrajectory("striker", true, listOf(Vec2(20.0, 30.0), Vec2(40.0, 50.0)))
        val candidate = RenderedCandidate("A", listOf(path), emptyList(), 0.6, 0.8)
        assertThrows(IllegalArgumentException::class.java) { OverlayScene(bounds, listOf(candidate), "missing") }
        assertThrows(IllegalArgumentException::class.java) { OverlayScene(bounds, listOf(candidate, candidate), "A") }
        val outside = BodyTrajectory("disc", false, listOf(Vec2(0.0, 0.0), Vec2(40.0, 50.0)))
        val invalid = RenderedCandidate("B", listOf(outside), emptyList(), 0.6, null)
        assertThrows(IllegalArgumentException::class.java) { OverlayScene(bounds, listOf(invalid), "B") }
        assertEquals(0, OverlayScene(bounds, emptyList(), null).candidates.size)
    }

    @Test
    fun markersAndCandidateListsAreOwnedAndValidated() {
        val path = BodyTrajectory("disc", false, listOf(Vec2(20.0, 30.0), Vec2(40.0, 50.0)))
        val annotations = mutableListOf(ShotAnnotation(AnnotationKind.CONTACT, Vec2(40.0, 50.0)))
        val candidate = RenderedCandidate("A", listOf(path), annotations, 1.0, 0.0)
        val candidates = mutableListOf(candidate)
        val scene = OverlayScene(bounds, candidates, "A")
        annotations.clear()
        candidates.clear()
        assertEquals(
            1,
            scene.candidates
                .single()
                .annotations.size,
        )
        val invalid = ShotAnnotation(AnnotationKind.POCKET, Vec2(1000.0, 1000.0))
        assertThrows(IllegalArgumentException::class.java) {
            OverlayScene(bounds, listOf(RenderedCandidate("B", listOf(path), listOf(invalid), 1.0, null)), "B")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RenderedCandidate("A", listOf(path, path), emptyList(), 1.0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RenderedCandidate("A", listOf(path), emptyList(), Double.POSITIVE_INFINITY, null)
        }
    }
}
