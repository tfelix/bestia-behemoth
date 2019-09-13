package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackType
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.BattleDamageComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Element
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.map.MapService
import org.springframework.stereotype.Service
import java.util.concurrent.ThreadLocalRandom

private val LOG = KotlinLogging.logger { }

/**
 * This service is used to perform attacks and damage calculation for battle
 * related tasks.
 *
 * @author Thomas Felix
 */
@Service
class BattleService(
    private val mapService: MapService,
    private val entityCollisionService: EntityCollisionService,
    private val damageCalculator: DamageCalculator
) {
  private val rand = ThreadLocalRandom.current()

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
  fun attackGround(attack: Attack, attacker: Entity, target: Point) {
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
      LOG.debug { "Defending entity is a bestia." }
      attackDamagableEntity(attack, attacker, defender)
    } else {
      LOG.warn("Entity can not receive damage because of missing components.")
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

    val primaryDamage = when(isCritical) {
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

    return if (rand.nextFloat() < hitrate) {
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
    return (isInRange(battleCtx)
        && hasLineOfSight(battleCtx)
        && hasAttackerEnoughMana(battleCtx)
        && hasAmmo(battleCtx))
  }

  /**
   * Checks if a given attack is in range for a target position. It is
   * important to ask the attached entity scripts as these can alter the
   * effective range.
   *
   * @return TRUE if the attack is in range. FALSE otherwise.
   */
  private fun isInRange(battleCtx: BattleContext): Boolean {
    val atkPosition = getPosition(battleCtx.attacker)
    val defPosition = getPosition(battleCtx.defender)

    val effectiveRange = getEffectiveSkillRange(battleCtx.usedAttack, battleCtx.attacker)

    LOG.trace("Effective attack range: {}", effectiveRange)

    return effectiveRange >= atkPosition.getDistance(defPosition)
  }

  /**
   * Checks if there is a direct line of sight between the two points. This
   * does not only take static map features into account but also dynamic
   * effects like entities which might block the direct line of sight.
   *
   * @return Returns TRUE if there is a direct line of sight. FALSE if there
   * is no direct line of sight.
   */
  private fun hasLineOfSight(battleCtx: BattleContext): Boolean {
    val attack = battleCtx.usedAttack
    val attacker = battleCtx.attacker
    val defender = battleCtx.defender

    if (!attack.needsLineOfSight) {
      LOG.trace("Attack does not need los.")
      return true
    }

    val start = attacker.getComponent(PositionComponent::class.java).position
    val end = defender.getComponent(PositionComponent::class.java).position

    val x1: Long
    val x2: Long
    val y1: Long
    val y2: Long
    x1 = Math.min(start.x, end.x)
    x2 = Math.max(start.x, end.x)
    y1 = Math.min(start.y, end.y)
    y2 = Math.max(start.y, end.y)

    val width = x2 - x1
    val height = y2 - y1

    val bbox = Rect(x1, y1, width, height)

    val map = mapService.getMap(bbox)

    val lineOfSight = lineOfSight(start, end)
    val doesMapBlock = lineOfSight.any { map.blocksSight(it) }
    val doesEntityBlock = entityCollisionService.getAllCollidingEntityIds(lineOfSight).isNotEmpty()

    val hasLos = !doesMapBlock && !doesEntityBlock
    LOG.trace("Entity has line of sight: {}", hasLos)
    return hasLos
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

    return if (rand.nextFloat() < crit) {
      LOG.trace("Attack was critical hit.")
      dmgVars.isCriticalHit = true
      true
    } else {
      LOG.trace("Attack did not critical hit.")
      dmgVars.isCriticalHit = false
      false
    }
  }

  /**
   * Calculates the needed mana for an attack. Mana cost can be reduced by
   * effects or scripts.
   *
   * @param battleCtx The [BattleContext].
   * @return The actual mana costs for this attack.
   */
  private fun getNeededMana(battleCtx: BattleContext): Int {
    val attack = battleCtx.usedAttack
    val neededManaMod = battleCtx.damageVariables.neededManaMod
    val neededMana = Math.ceil((attack.manaCost * neededManaMod).toDouble()).toInt()
    LOG.trace("Needed mana: {}/{}", neededMana, attack.manaCost)

    return neededMana
  }

  /**
   * Check if the entity has the mana needed for the attack.
   *
   * @return TRUE if the entity has enough mana to perform the attack. FALSE
   * otherwise.
   */
  private fun hasAttackerEnoughMana(battleCtx: BattleContext): Boolean {
    val neededMana = getNeededMana(battleCtx)
    return battleCtx.attackerCondition.currentMana >= neededMana
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

    val statusComp = defender.getComponent(StatusComponent::class.java)

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

    val statusComp = defender.getComponent(StatusComponent::class.java)

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

    val condValues = statusComp.conditionValues
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
   * Calculates a list of points which lie under the given line of sight. This
   * uses Bresenham line algorithm.
   *
   * @param start Starting point.
   * @param end   End point.
   * @return A list of points which are under the
   */
  private fun lineOfSight(start: Point, end: Point): List<Point> {
    val result = mutableListOf<Point>()
    val dx = end.x - start.x
    val dy = end.y - start.y
    var D = 2 * dy - dx
    var y = start.y

    for (x in start.x..end.x) {
      result.add(Point(x, y))
      if (D > 0) {
        y += 1
        D -= 2 * dx
      }
      D += 2 * dy
    }

    return result
  }

  /**
   * Calculates the effective range of the attack. A skill range can be
   * altered by an equipment or a buff for example.
   */
  private fun getEffectiveSkillRange(attack: Attack, entity: Entity): Int {
    // TODO Take status modifications into account.
    return attack.range
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
    return e.getComponent(StatusComponent::class.java).conditionValues
  }

  /**
   * @return The level of the entity.
   */
  private fun getLevel(e: Entity): Int {
    return e.getComponent(LevelComponent::class.java).level
  }

  private fun getPosition(e: Entity): Point {
    return e.getComponent(PositionComponent::class.java).position
  }
}
