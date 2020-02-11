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
    private val checkFactory: AttackCheckFactoryImpl,
    private val attackStrategyFactory: AttackStrategyFactory
) {

  /**
   * Default damage calculation for entity hits.
   *
   * @param attack The used attack by the attacker.
   * @param attacker   The entity attacking.
   * @param defender   The defending entity.
   * @return The calculated damage object or null if the attack failed or was not possible.
   */
  fun attackEntity(attack: Attack, attacker: Entity, defender: Entity): List<Damage> {
    LOG.trace("Entity {} attacks entity {} with {}.", attacker, defender, attack)

    // Prepare the battle context since this is needed to carry all information.
    val battleCtx = createBattleContext(attack, attacker, defender)

    if (!isAttackPossible(battleCtx)) {
      return emptyList()
    }

    val strategy = attackStrategyFactory.getAttackStrategy(battleCtx)
    val damages = strategy.doAttack()

    // TODO Perform possible damage reductions/hooks then apply the damage or do this in strategy? Possibly not
  }

  private fun createBattleContext(usedAttack: Attack, attacker: Entity, defender: Entity): BattleContext {
    val dmgVars = getDamageVars(attacker)

    return BattleContext(
        usedAttack = usedAttack,
        attacker = attacker,
        defender = defender,
        damageVariables = dmgVars,
        attackElement = Element.NORMAL, // FIXME, Depends on Weapon, Ammunition, Buff
        defenderElement = Element.NORMAL,
        weaponAtk = 1f // FIXME When Equipment is implemented use this to get meaningful value
    )
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
}
