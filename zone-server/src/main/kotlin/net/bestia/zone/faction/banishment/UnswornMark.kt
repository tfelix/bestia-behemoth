package net.bestia.zone.faction.banishment

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Keeps [StatusEffectId.UNSWORN] on a master exactly as long as its membership says it is unsworn. */
@Component
class UnswornMark(
  private val statusEffects: StatusEffectService,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun renew(world: World, master: EntityId) {
    val until = world.get(master, FactionMembership::class)?.unswornUntil
    val left = until?.let { Duration.between(clock.instant(), it) }?.takeIf { it > Duration.ZERO }
    val effects = world.get(master, StatusEffects::class)

    when {
      left == null -> effects?.removeEffect(StatusEffectId.UNSWORN.id)
      effects?.hasEffect(StatusEffectId.UNSWORN.id) == true -> Unit
      else -> statusEffects.applyEffect(world, master, StatusEffectId.UNSWORN, level = 1, durationSeconds = left.toMillis() / 1000.0)
    }
  }
}
