package net.bestia.zone.battle.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a ticking status effect gets to act with. The effect service is handed in rather than injected into
 * the script, because the service's script registry holds every script and would close a dependency cycle.
 */
class StatusEffectTickContext(
  val world: World,
  val hostId: EntityId,
  val level: Int,
  private val statusEffectService: StatusEffectService,
) {

  /** Credited to the host, so the target's effect names who gave it. */
  fun applyEffect(targetId: EntityId, effect: StatusEffectId, level: Int) {
    statusEffectService.applyEffect(world, targetId, effect, level, sourceEntityId = hostId)
  }
}
