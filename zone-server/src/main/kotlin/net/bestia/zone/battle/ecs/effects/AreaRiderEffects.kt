package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ForcedMovement
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Carries out an area's [AreaRiders] on one entity inside it. */
@Component
class AreaRiderEffects(
  private val statusEffects: StatusEffectService,
  private val forcedMovement: ForcedMovement,
) {

  fun onFoe(world: World, victimId: EntityId, effect: AreaEffect, centre: Vec3L) {
    val riders = effect.riders

    riders.statuses.forEach { status ->
      statusEffects.tryInflict(
        world, victimId, status.effect, status.level, effect.casterId, status.chancePercent, status.durationSeconds
      )
    }
    if (!riders.forAllies) {
      riders.zoneStatus?.let { statusEffects.applyEffect(world, victimId, it, effect.skillLevel, effect.casterId) }
    }
    if (riders.knockbackTiles > 0) {
      forcedMovement.awayFrom(world, victimId, centre, riders.knockbackTiles)
    }
  }

  fun onAlly(world: World, allyId: EntityId, effect: AreaEffect) {
    val riders = effect.riders
    if (!riders.forAllies) return

    riders.zoneStatus?.let { statusEffects.applyEffect(world, allyId, it, effect.skillLevel, effect.casterId) }
    if (riders.healPerTick > 0) {
      Healing.heal(world, allyId, riders.healPerTick)
    }
  }

  fun onAnyone(world: World, id: EntityId, effect: AreaEffect) {
    val percent = effect.riders.manaPercentPerTick
    if (percent <= 0) return

    world.get(id, Mana::class)?.let { it.current += it.max * percent / 100 }
  }
}
