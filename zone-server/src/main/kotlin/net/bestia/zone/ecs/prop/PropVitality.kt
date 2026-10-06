package net.bestia.zone.ecs.prop

import net.bestia.zone.ecs.core.Component

/**
 * How much punishment a static entity can take, before it has taken any.
 *
 * Not [net.bestia.zone.ecs.battle.status.Health], for the reason [PropPose] is not `Position`, and with a
 * second one on top: being in the `Health` store puts an entity in front of `HpRegenSystem`, `DeathSystem` and
 * `ReceivedDamageSystem`, all of which query it directly. A pristine tree has nothing for any of them to do.
 *
 * `Health` is added by the promotion path on the first point of damage, seeded from this.
 */
data class PropVitality(val maxHp: Int) : Component
