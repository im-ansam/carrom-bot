package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import kotlin.math.hypot

internal data class ContactBody(
    val id: String,
    val circle: Circle,
    val velocity: Vec2,
    val mass: Double,
)

/** Frictionless normal impulses; separation is mass weighted and never adds velocity. */
internal class CollisionResolver(
    private val config: PhysicsConfig,
) {
    fun resolve(
        a: ContactBody,
        b: ContactBody,
    ): Pair<ContactBody, ContactBody> {
        val delta = Vec2(b.circle.center.x - a.circle.center.x, b.circle.center.y - a.circle.center.y)
        val distance = hypot(delta.x, delta.y)
        val normal = if (distance == 0.0) Vec2(1.0, 0.0) else Vec2(delta.x / distance, delta.y / distance)
        // Normalized inverse masses avoid overflowing a mass sum or an inverse-mass sum.
        val scale = minOf(a.mass, b.mass)
        val inverseA = scale / a.mass
        val inverseB = scale / b.mass
        val weightA = inverseA / (inverseA + inverseB)
        val weightB = inverseB / (inverseA + inverseB)
        val relative = (b.velocity.x - a.velocity.x) * normal.x + (b.velocity.y - a.velocity.y) * normal.y
        require(relative.isFinite()) { "Unrepresentable relative velocity" }
        val change = if (relative < 0.0) -(1.0 + config.restitution) * relative else 0.0
        require(change.isFinite()) { "Unrepresentable impulse" }
        val penetration = (a.circle.radius + b.circle.radius - distance).coerceAtLeast(0.0)
        val correction = if (penetration > 0.0) penetration + config.contactTolerance else 0.0
        return a.copy(
            circle = a.circle.copy(center = offset(a.circle.center, normal, -correction * weightA)),
            velocity = offset(a.velocity, normal, -change * weightA),
        ) to
            b.copy(
                circle = b.circle.copy(center = offset(b.circle.center, normal, correction * weightB)),
                velocity = offset(b.velocity, normal, change * weightB),
            )
    }

    private fun offset(
        value: Vec2,
        normal: Vec2,
        amount: Double,
    ): Vec2 = Vec2(value.x + normal.x * amount, value.y + normal.y * amount)
}
