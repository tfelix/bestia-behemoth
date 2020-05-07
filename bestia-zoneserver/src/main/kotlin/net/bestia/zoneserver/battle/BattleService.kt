package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.AttackRepository
import net.bestia.model.battle.Damage
import net.bestia.model.battle.Element
import net.bestia.model.findOneOrThrow
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.battle.damage.DamageVariables
import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This high level service which is used to perform attacks and damage calculation for battle
 * related tasks.
 *
 * @author Thomas Felix
 */
@Service
class BattleService(
    private val checkFactory: AttackCheckFactoryImpl,
    private val attackRepository: AttackRepository,
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
  fun attackEntity(attackId: Long, attacker: Entity, defender: Entity): List<Damage> {
    val attack = attackRepository.findOneOrThrow(attackId)
    val battleAttack = BattleAttack.fromAttack(attack)

    return attackEntity(battleAttack, attacker, defender)
  }

  fun attackEntity(attack: BattleAttack, attacker: Entity, defender: Entity): List<Damage> {
    LOG.trace { "Entity $attacker attacks entity $defender with $attack" }

    // Prepare the battle context since this is needed to carry all information.
    val battleCtx = createBattleContext(attack, attacker, defender)

    if (!isAttackPossible(battleCtx)) {
      return emptyList()
    }

    val strategy = attackStrategyFactory.getAttackStrategy(battleCtx)
    val damages = strategy.doAttack()

    // TODO Perform possible damage reductions/hooks then apply the damage or do this in strategy? Possibly not

    return damages
  }


  fun attackGround(attackId: Long, attacker: Entity, pos: Vec3) {

  }

  private fun createBattleContext(usedAttack: BattleAttack, attacker: Entity, defender: Entity): BattleContext {
    val dmgVars = getDamageVars(attacker)

    return BattleContext(
        usedAttack = usedAttack,
        attacker = attacker,
        defender = defender,
        damageVariables = dmgVars,
        attackElement = Element.NORMAL, // FIXME, Depends on Weapon, Ammunition, Buff
        defenderElement = Element.NORMAL,
        weaponAtk = 0f // FIXME When Equipment is implemented use this to get meaningful value
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
}
