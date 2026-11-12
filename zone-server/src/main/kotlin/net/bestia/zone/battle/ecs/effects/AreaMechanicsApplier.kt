package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.skill.PartyMates
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
    if (!mechanics.forAllies && !mechanics.zoneStatusForEveryone) {
      mechanics.zoneStatus?.let { statusEffects.applyEffect(world, victimId, it, effect.skillLevel, effect.casterId) }
    }
    if (mechanics.knockbackTiles > 0 && !Knockback.isImmune(world, victimId)) {
      val across = mechanics.knockbackAcross
      val at = world.get(victimId, Position::class)?.toVec3L()
      if (across != null && at != null) {
        val push = sideItCameFrom(world.get(victimId, Facing::class), across)
        // A point on the far side is what the victim is pushed away from.
        forcedMovement.awayFrom(world, victimId, Vec3L(at.x - push * across.x, at.y - push * across.y, at.z), mechanics.knockbackTiles)
      } else {
        forcedMovement.awayFrom(world, victimId, centre, mechanics.knockbackTiles)
      }
    }
  }

  /**
   * +1 to push along [across], -1 against it: back against the victim's facing where that crosses the wall. A foe
   * that faces along the wall gives no hint, and is pushed away from the caster's side.
   */
  private fun sideItCameFrom(facing: Facing?, across: Vec3L): Int {
    val headingAcross = facing?.let { it.dx * across.x + it.dy * across.y } ?: 0L

    return if (headingAcross > 0) -1 else 1
  }

  fun onAlly(world: World, allyId: EntityId, effect: AreaEffect, areaId: EntityId? = null) {
    val mechanics = effect.mechanics
    if (!mechanics.forAllies) return
    if (mechanics.partyOnly && !PartyMates.isPartyOf(world, effect.casterId, allyId)) return

    if (!mechanics.zoneStatusForEveryone) {
      val source = areaId.takeIf { mechanics.absorbsHits > 0 } ?: effect.casterId
      mechanics.zoneStatus?.let { statusEffects.applyEffect(world, allyId, it, effect.skillLevel, source) }
    }
    if (mechanics.healPerTick > 0) {
      Healing.heal(world, allyId, mechanics.healPerTick)
    }
    if (mechanics.healPercentPerTick > 0) {
      val maxHealth = world.get(allyId, Health::class)?.max ?: return
      Healing.heal(world, allyId, maxHealth * mechanics.healPercentPerTick / 100)
    }
  }

  fun onAnyone(world: World, id: EntityId, effect: AreaEffect) {
    val mechanics = effect.mechanics
    if (mechanics.zoneStatusForEveryone) {
      mechanics.zoneStatus?.let { statusEffects.applyEffect(world, id, it, effect.skillLevel, effect.casterId) }
    }

    val percent = mechanics.manaPercentPerTick
    if (percent <= 0) return

    world.get(id, Mana::class)?.let { it.current += it.max * percent / 100 }
  }
}
