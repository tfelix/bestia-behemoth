package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackType
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Element
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Service
import kotlin.random.Random

private val LOG = KotlinLogging.logger { }

interface AttackCheckFactory {
  fun buildCheckFor(battleCtx: BattleContext): AttackCheck
  fun canBuildFor(battleCtx: BattleContext): Boolean
}

/**
 * This high level service which is used to perform attacks and damage calculation for battle
 * related tasks.
 *
 * @author Thomas Felix
 */
@Service
class BattleService(
    private val damageCalculator: DamageCalculator,
    private val checkFactory: AttackCheckFactoryImpl,
    private val random: Random
) {

  /**
   * Attacks itself.
   */
  fun attackSelf(attack: Attack, self: Entity) {
    // FIXME Reparieren.
    throw IllegalStateException("Not yet implemented.")
  }

  /**
   * Performs an attack/skill against a ground target. This will usually spawn
   * an entity doing AOE damage over time but pre-attack checks have to be
   * made as well.
   *
   * @param target Point to attack.
   */
  fun attackGround(attack: Attack, attacker: Entity, target: Vec3) {
    // FIXME Reparieren.
    throw IllegalStateException("Not yet implemented.")
  }

  /**
   * This method should be used if a entity directly attacks another entity.
   * Both entities must posess a [StatusComponent] for the calculation
   * to take place. If this is missing an [IllegalArgumentException]
   * will be thrown.
   */
  fun attackEntity(attack: Attack, attacker: Entity, defender: Entity): List<Damage>? {
    LOG.trace("Entity {} attacks entity {} with {}.", attacker, defender, attack)

    return if (isEligibleForDamage(defender)) {
      attackDamagableEntity(attack, attacker, defender)
    } else {
      LOG.warn("Entity $defender can not receive damage because of missing components")
      null
    }
  }

  /**
   * Default damage calculation for entity hits.
   *
   * @param attack The used attack by the attacker.
   * @param attacker   The entity attacking.
   * @param defender   The defending entity.
   * @return The calculated damage object or null if the attack failed or was not possible.
   */
  private fun attackDamagableEntity(attack: Attack, attacker: Entity, defender: Entity): List<Damage>? {
    // Prepare the battle context since this is needed to carry all information.
    val battleCtx = createBattleContext(attack, attacker, defender)

    if (!isAttackPossible(battleCtx)) {
      LOG.trace("Attack was not possible.")
      return null
    }

    val isCritical = isCriticalHit(battleCtx)

    if (!doesAttackHit(battleCtx)) {
      return listOf(Damage.miss)
    }

    val damageValue = damageCalculator.calculateDamage(battleCtx)
    LOG.trace("Primary damage calculated: {}", damageValue)

    val primaryDamage = when (isCritical) {
      true -> Damage.getCrit(damageValue)
      false -> Damage.getHit(damageValue)
    }
    // Damage can now be reduced by effects.
    val (defender, attacker, damage) = takeDamage(defender, primaryDamage, attacker)
    LOG.trace("Entity {} received damage: {}", defender, primaryDamage)

    return listOf(damage)
  }

  private fun createBattleContext(usedAttack: Attack, attacker: Entity, defender: Entity): BattleContext {
    val dmgVars = getDamageVars(attacker)

    val atkStatus = getStatusPoints(attacker)
    val defStatus = getStatusPoints(defender)

    val atkStatusBased = getStatBased(attacker)
    val defStatusBased = getStatBased(defender)

    val atkCond = getConditional(attacker)
    val defCond = getConditional(defender)

    return BattleContext(
        usedAttack = usedAttack,
        attacker = attacker,
        defender = defender,
        damageVariables = dmgVars,
        attackerStatusPoints = atkStatus,
        defenderStatusPoints = defStatus,
        attackerCondition = atkCond,
        defenderCondition = defCond,
        attackerStatusBased = atkStatusBased,
        defenderStatusBased = defStatusBased,
        attackElement = Element.NORMAL, // FIXME
        defenderElement = Element.NORMAL,
        weaponAtk = 1f // FIXME When Equipment is implemented use this to get meaningful value
    )
  }

  /**
   * It must be checked if an entity is eligible for receiving damage. This
   * means that an [StatusComponent] as well as a
   * [PositionComponent] must be present.
   *
   * @return TRUE if the entity is abtle to receive damage. FALSE otherwise.
   */
  private fun isEligibleForDamage(entity: Entity): Boolean {
    // Check if we have valid x and y.
    if (!entity.hasComponent(StatusComponent::class.java)) {
      LOG.warn("Entity {} does not have status component.", entity)
      return false
    }

    if (!entity.hasComponent(PositionComponent::class.java)) {
      LOG.warn("Entity {} does not have position component.", entity)
      return false
    }

    if (!entity.hasComponent(LevelComponent::class.java)) {
      LOG.warn("Entity {} does not have level component.", entity)
      return false
    }

    return true
  }

  /**
   * Checks if the attack is able to hit its target.
   *
   * @return TRUE if the attack hits the target. FALSE otherwise.
   */
  private fun doesAttackHit(battleCtx: BattleContext): Boolean {
    val attack = battleCtx.usedAttack
    val attacker = battleCtx.attacker
    val defender = battleCtx.defender
    val isCriticalHit = battleCtx.damageVariables.isCriticalHit

    LOG.trace("Calculate hit.")

    val atkType = attack.type

    if (atkType == AttackType.MELEE_MAGIC
        || atkType == AttackType.RANGED_MAGIC
        || atkType == AttackType.NO_DAMAGE) {
      LOG.trace("Non physical attacks always hits.")
      return true
    }

    val atkStatBased = getStatBased(attacker)
    val defStatBased = getStatBased(defender)

    var hitrate = 0.5f * atkStatBased.hitrate / defStatBased.dodge

    if (isCriticalHit) {
      hitrate *= 3f
    }

    hitrate = hitrate.clamp(0.05f, 1f)

    LOG.trace("Hit chance: {}", hitrate)

    return if (random.nextFloat() < hitrate) {
      LOG.trace("Attack was hit.")
      true
    } else {
      LOG.trace("Attack did not hit.")
      false
    }
  }

  /**
   * Checks if an attack is even possible at all. Attacks only succeed if
   * certain preconditions like line of sight, ammo and mana are not missing.
   *
   * @return TRUE if the attack action is possible. FALSE otherwise.
   */
  private fun isAttackPossible(battleCtx: BattleContext): Boolean {
    val check = checkFactory.buildChecker(battleCtx)

    return check.isAttackPossible()
  }

  /**
   * Checks if the attack performs a critical hit onto the target. The outcome
   * of the critical hit check is then saved into damage variables.
   */
  private fun isCriticalHit(battleCtx: BattleContext): Boolean {
    val attack = battleCtx.usedAttack
    val attacker = battleCtx.attacker
    val defender = battleCtx.defender
    val dmgVars = battleCtx.damageVariables

    LOG.trace("Calculating: criticalHit")

    if (attack.isMagic || attack.type == AttackType.NO_DAMAGE) {
      LOG.trace("Attack is magic. Can not hit critical.")
      dmgVars.isCriticalHit = false
      return false
    }

    val atkLv = getLevel(attacker)
    val defLv = getLevel(defender)

    val atkStatus = getStatusPoints(attacker)
    val defStatus = getStatusPoints(defender)

    val atkDex = atkStatus.dexterity.toFloat()
    val defDex = defStatus.dexterity.toFloat()

    val atkAgi = atkStatus.agility.toFloat()
    val defAgi = defStatus.agility.toFloat()

    var crit = (0.02f + (atkLv / defLv / 5).toFloat()
        + atkDex / defDex / 2
        + atkAgi / defAgi / 2) * dmgVars.criticalChanceMod

    crit = crit.clamp(0.01f, 0.95f)

    LOG.trace("Crit chance: {}", crit)

    return if (random.nextFloat() < crit) {
      LOG.trace("Attack was critical hit.")
      dmgVars.isCriticalHit = true
      true
    } else {
      LOG.trace("Attack did not critical hit.")
      dmgVars.isCriticalHit = false
      false
    }
  }

  private fun hasAmmo(battleCtx: BattleContext): Boolean {
    LOG.warn("hasAmmo currently not implemented.")
    return true
  }

  /**
   * Gets the current damage variables of an entity counting for itself. This
   * usually boosts the own values for more damage. This function will also
   * invoke all the scripts currently attached to the entity which might alter
   * the damage var.
   *
   * @param e The entity to get the damage vars for.
   * @return The current damage vars of the entity.
   */
  private fun getDamageVars(e: Entity): DamageVariables {
    // FIXME Implementieren.
    return DamageVariables()
  }

  /**
   * The true damage is applied directly to the entity without further
   * reducing the damage via armor.
   *
   * @param defender
   * @param trueDamage
   */
  fun takeTrueDamage(defender: Entity, trueDamage: Damage): Entity {
    val damage = trueDamage.damage

    val statusComp = defender.getComponent(ConditionComponent::class.java)

    statusComp.conditionValues.addHealth(-damage)

    if (statusComp.conditionValues.currentHealth == 0) {
      killEntity(defender)
    }

    return defender
  }

  /**
   * This will perform a check damage for reducing it and alter all possible
   * status effects and then apply the damage to the entity. If its health
   * sinks below 0 then the [.killEntity] method will be triggered. It will
   * also trigger any attached script trigger for received damage this is
   * onTakeDamage and onApplyDamage.
   *
   * @param primaryDamage The damage to apply to this entity.
   * @return The actually applied damage.
   */
  fun takeDamage(defender: Entity, primaryDamage: Damage, attacker: Entity? = null): Triple<Entity?, Entity, Damage> {
    LOG.trace("Entity {} takes damage: {}.", defender, primaryDamage)

    val conditionComp = defender.getComponent(ConditionComponent::class.java)

    // TODO Possibly reduce the damage via effects or scripts.
    val damage = primaryDamage.damage
    val reducedDamage = Damage(damage, primaryDamage.type)

    // Hit the entity and add the origin entity into the list of received
    // damage dealers.
    val battleComp = defender.tryGetComponent(BattleDamageComponent::class.java)
        ?: BattleDamageComponent(defender.id)

    attacker?.let {
      battleComp.addDamageReceived(it.id, damage.toLong())
    }

    val condValues = conditionComp.conditionValues
    condValues.addHealth(-damage)

    if (condValues.currentHealth == 0) {
      killEntity(defender)
    }

    return Triple(attacker, defender, reducedDamage)
  }

  /**
   * Entity received damage with not entity as origin. Just plain damage.
   *
   * @param defender
   * @param damage
   * @return
   */
  fun takeDamage(defender: Entity, damage: Int): Pair<Entity, Damage> {
    val dmg = Damage.getHit(damage)
    val (_, defender, damageTaken) = takeDamage(defender, dmg)

    return Pair(defender, damageTaken)
  }

  fun killEntity(entity: Entity) {
    LOG.debug { "Entity ${entity.id} killed" }

    // Entity will play death animation.

    // If its player entity then set
  }

  /**
   * @return The [StatusValues] of a entity.
   */
  private fun getStatusPoints(e: Entity): StatusValues {
    return e.getComponent(StatusComponent::class.java).statusValues
  }

  /**
   * @return [StatusBasedValues] of the entity.
   */
  private fun getStatBased(e: Entity): StatusBasedValues {
    return e.getComponent(StatusComponent::class.java).statusBasedValues
  }

  /**
   * @return [StatusBasedValues] of the entity.
   */
  private fun getConditional(e: Entity): ConditionValues {
    return e.getComponent(ConditionComponent::class.java).conditionValues
  }

  /**
   * @return The level of the entity.
   */
  private fun getLevel(e: Entity): Int {
    return e.getComponent(LevelComponent::class.java).level
  }
}
