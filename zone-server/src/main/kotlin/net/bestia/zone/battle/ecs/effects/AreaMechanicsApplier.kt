package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ForcedMovement
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Carries out an area's [AreaMechanics] on one entity inside it. */
@Component
class AreaMechanicsApplier(
  private val statusEffects: StatusEffectService,
  private val forcedMovement: ForcedMovement,
) {

  fun onFoe(world: World, victimId: EntityId, effect: AreaEffect, centre: Vec3L) {
    val mechanics = effect.mechanics

    mechanics.statuses.forEach { status ->
      statusEffects.tryInflict(
        world, victimId, status.effect, status.level, effect.casterId, status.chancePercent, status.durationSeconds
      )
    }
    if (!mechanics.forAllies) {
      mechanics.zoneStatus?.let { statusEffects.applyEffect(world, victimId, it, effect.skillLevel, effect.casterId) }
    }
    if (mechanics.knockbackTiles > 0) {
      forcedMovement.awayFrom(world, victimId, centre, mechanics.knockbackTiles)
    }
  }

  fun onAlly(world: World, allyId: EntityId, effect: AreaEffect) {
    val mechanics = effect.mechanics
    if (!mechanics.forAllies) return

    mechanics.zoneStatus?.let { statusEffects.applyEffect(world, allyId, it, effect.skillLevel, effect.casterId) }
    if (mechanics.healPerTick > 0) {
      Healing.heal(world, allyId, mechanics.healPerTick)
    }
  }

  fun onAnyone(world: World, id: EntityId, effect: AreaEffect) {
    val percent = effect.mechanics.manaPercentPerTick
    if (percent <= 0) return

    world.get(id, Mana::class)?.let { it.current += it.max * percent / 100 }
  }
}
