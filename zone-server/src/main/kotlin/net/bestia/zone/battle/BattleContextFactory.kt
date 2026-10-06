package net.bestia.zone.battle

import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.DerivedStatusValues
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatBonus
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Projects live ECS state onto the [BattleContext] value objects the damage calculation works with.
 * This is the bridge that was missing between the ECS and the (previously unreachable) battle
 * package - nothing built a [BattleEntity] from an entity id before.
 *
 * Must be called with the world to itself; callers pass the [World] they already have (a system on the
 * tick thread, or the receiver inside a `WorldView.read/modify` block).
 */
@Component
class BattleContextFactory(
  private val propPromotion: CombatTargetPromotion,
) {

  /**
   * Builds the context for [attackerId] using [usedAttack] against either an entity or a ground
   * position. Returns null when either side is missing the components a fight needs (e.g. the target
   * died or despawned during a cast).
   */
  fun create(
    world: World,
    attackerId: EntityId,
    usedAttack: BattleAttack,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ): BattleContext? {
    val attacker = battleEntity(world, attackerId) ?: return null

    if (targetEntityId != null) {
      // A defensive, idempotent no-op for anything already promoted or never a prop; the call site that
      // actually matters for a channelled skill is ActivateSkillHandler - see PropPromotionService's own KDoc
      // for why calling it only here would fizzle the first hit of a channelled cast.
      val reach = CombatTargetPromotion.TARGETING_REACH
      if (!propPromotion.promoteIfNeeded(world, targetEntityId, attacker.position, reach)) return null

      val defender = battleEntity(world, targetEntityId) ?: return null

      return EntityBattleContext(
        usedAttack = usedAttack,
        attacker = attacker,
        weapon = equippedWeapon(world, attackerId),
        damageVariables = DamageVariables(),
        defender = defender
      )
    }

    return GroundBattleContext(
      usedAttack = usedAttack,
      attacker = attacker,
      weapon = equippedWeapon(world, attackerId),
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

    val combatBonus = world.get(entityId, CombatBonus::class)

    return BattleEntity(
      id = entityId,
      position = position,
      level = level,
      // Soft defense per the docs' SoftDEF/SoftMDEF formulas, plus whatever the entity is wearing. A
      // null CombatBonus - every mob, every promoted prop - means it wears nothing.
      defense = DefenseValues.fromStatusValues(
        lv = level,
        sv = statusValues,
        hardDefense = combatBonus?.hardDefense ?: 0,
        hardMagicDefense = combatBonus?.hardMagicDefense ?: 0
      ),
      statusValues = statusValues,
      derivedStatusValues = DerivedStatusValues.fromStatusValues(level, statusValues),
      // Armour and buffs that change it do not exist yet, so the species' own element is the whole answer.
      assumedElement = world.get(entityId, Nature::class)?.element ?: Element.NORMAL,
      maxHealth = world.get(entityId, Health::class)?.max ?: 0,
      activeEffectIds = world.get(entityId, StatusEffects::class)
        ?.activeEffects
        ?.mapTo(mutableSetOf()) { it.definitionId }
        ?: emptySet()
    )
  }

  /**
   * What the attacker is swinging, as the damage formula wants it.
   *
   * The attack comes off [CombatBonus] rather than off the equipment directly, because by the time it reaches
   * here it is already resolved: `StatusValueRecalcSystem` has run every worn item's script into one number,
   * so a two-handed weapon, a shield that bites, or a ring that adds flat attack all arrive the same way and
   * this does not have to know which slots can carry power.
   *
   * The refinement level travels apart from the attack, because it scales the weapon term specifically, not
   * the total. Absent anything, a bare-handed fighter, which is every mob.
   */
  private fun equippedWeapon(world: World, entityId: EntityId): Weapon {
    val bonus = world.get(entityId, CombatBonus::class)

    return Weapon(
      atk = bonus?.atk ?: 0,
      matk = bonus?.matk ?: 0,
      upgradeLevel = bonus?.weaponUpgradeLevel ?: 0
    )
  }
}
