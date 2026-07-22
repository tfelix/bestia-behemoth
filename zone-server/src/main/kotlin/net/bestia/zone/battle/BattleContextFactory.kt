package net.bestia.zone.battle

import net.bestia.zone.battle.skill.BattleSkill
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.DerivedStatusValues
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Projects live ECS state onto the [BattleContext] value objects the damage calculation works with.
 * This is the bridge that was missing between the ECS and the (previously unreachable) battle
 * package - nothing built a [BattleEntity] from an entity id before.
 *
 * Must be called while the world lock is held; callers pass the [World] they already have (a system
 * on the tick thread, or the receiver inside a `WorldView.read/modify` block).
 */
@Component
class BattleContextFactory {

  /**
   * Builds the context for [attackerId] using [usedAttack] against either an entity or a ground
   * position. Returns null when either side is missing the components a fight needs (e.g. the target
   * died or despawned during a cast).
   */
  fun create(
    world: World,
    attackerId: EntityId,
    usedAttack: BattleSkill,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ): BattleContext? {
    val attacker = battleEntity(world, attackerId) ?: return null

    if (targetEntityId != null) {
      val defender = battleEntity(world, targetEntityId) ?: return null

      return EntityBattleContext(
        usedAttack = usedAttack,
        attacker = attacker,
        weapon = equippedWeapon(),
        damageVariables = DamageVariables(),
        defender = defender
      )
    }

    return GroundBattleContext(
      usedAttack = usedAttack,
      attacker = attacker,
      weapon = equippedWeapon(),
      damageVariables = DamageVariables(),
      targetPosition = targetPosition ?: return null
    )
  }

  private fun battleEntity(world: World, entityId: EntityId): BattleEntity? {
    if (!world.isAlive(entityId)) {
      return null
    }

    val position = world.get(entityId, Position::class)?.toVec3L() ?: return null
    val attributes = world.get(entityId, StatusValues::class) ?: return null
    val level = world.get(entityId, Level::class)?.level ?: 1

    val statusValues = net.bestia.zone.battle.status.StatusValues(
      strength = attributes.strength,
      vitality = attributes.vitality,
      intelligence = attributes.intelligence,
      agility = attributes.agility,
      willpower = attributes.willpower,
      dexterity = attributes.dexterity
    )

    return BattleEntity(
      id = entityId,
      position = position,
      level = level,
      // Soft defense per the docs' SoftDEF/SoftMDEF formulas. Hard (equipment) defense is a
      // separate term still missing until an armour system lands.
      defense = DefenseValues.fromStatusValues(level, statusValues),
      statusValues = statusValues,
      derivedStatusValues = DerivedStatusValues.fromStatusValues(level, statusValues),
      // TODO No element component exists yet; everything is NORMAL until elements are modelled.
      assumedElement = Element.NORMAL
    )
  }

  // TODO There is no equipment system yet, so every entity fights bare-handed.
  private fun equippedWeapon() = Weapon(atk = 0, matk = 0, upgradeLevel = 0)
}
