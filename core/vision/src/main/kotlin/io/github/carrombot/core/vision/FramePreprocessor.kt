package io.github.carrombot.core.vision

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Native scratch storage is confined to one call; the caller owns the returned edge Mat. */
internal object FramePreprocessor {
    private const val LOW_THRESHOLD = 50.0
    private const val HIGH_THRESHOLD = 150.0
    private const val BLUR_SIZE = 5.0

    fun edges(rgba: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        var succeeded = false
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(BLUR_SIZE, BLUR_SIZE), 0.0)
            Imgproc.Canny(blurred, edges, LOW_THRESHOLD, HIGH_THRESHOLD)
            succeeded = true
            return edges
        } finally {
            if (!succeeded) edges.release()
            gray.release()
            blurred.release()
        }
    }
}
