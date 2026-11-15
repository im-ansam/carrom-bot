package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot

/** Finds the first approaching root of squared circle separation on piecewise quadratic paths. */
internal class SweptContact(
    private val config: PhysicsConfig,
) {
    private val friction = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)

    fun time(
        a: ContactBody,
        b: ContactBody,
        horizon: Double,
    ): Double {
        val boundaries =
            (listOf(0.0, horizon) + listOf(stopTime(a), stopTime(b)).filter { it > 0.0 && it < horizon })
                .distinct()
                .sorted()
        var result = horizon
        for ((start, end) in boundaries.zipWithNext()) {
            val duration = end - start
            val segmentA = at(a, start)
            val segmentB = at(b, start)
            val coefficients = coefficients(segmentA, segmentB, duration)
            rejectSustainedContact(segmentA, segmentB, coefficients)
            val derivative = derivative(coefficients)
            val root = roots(coefficients).firstOrNull { it > 0.0 && evaluate(derivative, it) < 0.0 }
            if (root != null) {
                result = start + root * duration
                break
            }
        }
        return result
    }

    private fun rejectSustainedContact(
        a: ContactBody,
        b: ContactBody,
        coefficients: List<Double>,
    ) {
        val distance = hypot(b.circle.center.x - a.circle.center.x, b.circle.center.y - a.circle.center.y)
        val touching = distance - a.circle.radius - b.circle.radius <= config.contactTolerance
        val stationaryNormal = coefficients[1] == 0.0
        // With zero first derivative, the first nonzero higher term determines whether
        // separation decreases immediately. An impulse cannot support this sustained load.
        val leadingTerm = coefficients.drop(2).firstOrNull { it != 0.0 } ?: 0.0
        require(!(touching && stationaryNormal && leadingTerm < 0.0)) {
            "Acceleration-driven sustained contact is unsupported"
        }
    }

    private fun stopTime(body: ContactBody): Double =
        friction?.stoppingTime(hypot(body.velocity.x, body.velocity.y)) ?: Double.POSITIVE_INFINITY

    private fun at(
        body: ContactBody,
        seconds: Double,
    ): ContactBody {
        val step =
            friction?.step(body.velocity, seconds) ?: FrictionStep(
                Vec2(body.velocity.x * seconds, body.velocity.y * seconds),
                body.velocity,
            )
        return body.copy(
            circle =
                body.circle.copy(
                    center =
                        Vec2(
                            body.circle.center.x + step.displacement.x,
                            body.circle.center.y + step.displacement.y,
                        ),
                ),
            velocity = step.velocity,
        )
    }

    private fun coefficients(
        a: ContactBody,
        b: ContactBody,
        duration: Double,
    ): List<Double> {
        val x = b.circle.center.x - a.circle.center.x
        val y = b.circle.center.y - a.circle.center.y
        val vx = (b.velocity.x - a.velocity.x) * duration
        val vy = (b.velocity.y - a.velocity.y) * duration
        val ax = (acceleration(b, true) - acceleration(a, true)) * duration / 2.0 * duration
        val ay = (acceleration(b, false) - acceleration(a, false)) * duration / 2.0 * duration
        val radius = a.circle.radius + b.circle.radius
        val scale = listOf(x, y, vx, vy, ax, ay, radius).maxOf { abs(it) }
        require(scale.isFinite() && radius / scale > 0.0) { "Unrepresentable swept geometry" }
        val px = listOf(x / scale, vx / scale, ax / scale)
        val py = listOf(y / scale, vy / scale, ay / scale)
        val values = MutableList(POLYNOMIAL_SIZE) { 0.0 }
        for (i in px.indices) for (j in px.indices) values[i + j] += px[i] * px[j] + py[i] * py[j]
        values[0] -= (radius / scale) * (radius / scale)
        return values
    }

    private fun acceleration(
        body: ContactBody,
        xAxis: Boolean,
    ): Double {
        val speed = hypot(body.velocity.x, body.velocity.y)
        return if (speed == 0.0) {
            0.0
        } else {
            -config.deceleration *
                (if (xAxis) body.velocity.x / speed else body.velocity.y / speed)
        }
    }

    /** Derivative roots partition [0,1] into monotone intervals; bisection has a fixed budget. */
    private fun roots(values: List<Double>): List<Double> {
        val degree = values.indexOfLast { it != 0.0 }
        if (degree <= 1) {
            return if (degree <= 0) emptyList() else listOf(-values[0] / values[1]).filter { it in 0.0..1.0 }
        }
        val critical = roots(derivative(values.take(degree + 1)))
        val boundaries = (listOf(0.0) + critical + 1.0).distinct().sorted()
        val result = boundaries.filter { evaluate(values, it) == 0.0 }.toMutableList()
        for ((start, end) in boundaries.zipWithNext()) {
            val first = evaluate(values, start)
            val last = evaluate(values, end)
            if (first != 0.0 && last != 0.0 && (first > 0.0) != (last > 0.0)) {
                var low = start
                var high = end
                repeat(ROOT_ITERATIONS) {
                    val middle = low + (high - low) / 2.0
                    if ((evaluate(values, middle) > 0.0) == (first > 0.0)) low = middle else high = middle
                }
                result.add(low + (high - low) / 2.0)
            }
        }
        return result.distinct().sorted()
    }

    private fun derivative(values: List<Double>): List<Double> =
        values.drop(1).mapIndexed { index, value ->
            val power = index + 1
            power * value
        }

    private fun evaluate(
        values: List<Double>,
        t: Double,
    ): Double = values.asReversed().fold(0.0) { sum, value -> sum * t + value }

    private companion object {
        const val POLYNOMIAL_SIZE = 5
        const val ROOT_ITERATIONS = 64
    }
}
