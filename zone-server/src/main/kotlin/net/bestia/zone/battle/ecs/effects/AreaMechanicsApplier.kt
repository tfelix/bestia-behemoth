package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.Knockback
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Facing
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
    if (mechanics.knockbackTiles > 0 && !Knockback.isImmune(world, victimId)) {
      val facing = world.get(victimId, Facing::class)
      val at = world.get(victimId, Position::class)?.toVec3L()
      if (mechanics.knockbackAgainstFacing && facing != null && at != null) {
        // Back the way it came: a point ahead of the victim is what it is pushed away from.
        forcedMovement.awayFrom(world, victimId, Vec3L(at.x + facing.dx, at.y + facing.dy, at.z), mechanics.knockbackTiles)
      } else {
        forcedMovement.awayFrom(world, victimId, centre, mechanics.knockbackTiles)
      }
    }
  }

  fun onAlly(world: World, allyId: EntityId, effect: AreaEffect) {
    val mechanics = effect.mechanics
    if (!mechanics.forAllies) return

    mechanics.zoneStatus?.let { statusEffects.applyEffect(world, allyId, it, effect.skillLevel, effect.casterId) }
    if (mechanics.healPerTick > 0) {
      Healing.heal(world, allyId, mechanics.healPerTick)
    }
    if (mechanics.healPercentPerTick > 0) {
      val maxHealth = world.get(allyId, Health::class)?.max ?: return
      Healing.heal(world, allyId, maxHealth * mechanics.healPercentPerTick / 100)
    }
  }

  fun onAnyone(world: World, id: EntityId, effect: AreaEffect) {
    val percent = effect.mechanics.manaPercentPerTick
    if (percent <= 0) return

    world.get(id, Mana::class)?.let { it.current += it.max * percent / 100 }
  }
}
