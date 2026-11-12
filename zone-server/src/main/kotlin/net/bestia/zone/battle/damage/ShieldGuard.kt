package net.bestia.zone.battle.damage

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * A shield that catches a weapon blow whole, at the block chance of the one it hits, see Auto Guard. Asked where a
 * blow lands, on the swing and the skill path, so the client shows "Block" instead of a number. A spell passes.
 */
@Component
class ShieldGuard(private val random: Random = ThreadLocalRandomSource) {

  /** [Blocked] when [targetId]'s shield catches [blow], else [blow] unchanged. */
  fun guard(world: World, targetId: EntityId, kind: HitKind, blow: Damage): Damage {
    if (kind.form != HitForm.PHYSICAL || (blow !is HitDamage && blow !is CriticalHit)) return blow
    val chance = world.get(targetId, CombatStats::class)?.values?.blockChancePercent ?: 0
    if (chance <= 0 || random.nextInt(100) >= chance) return blow

    return Blocked
  }
}
