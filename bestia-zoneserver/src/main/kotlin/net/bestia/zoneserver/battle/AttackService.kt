package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Provides all access to let entities learn attacks.
 *
 * @author Thomas Felix
 */
@Service
class AttackService(
    private val attackDao: AttackRepository
) {

  /**
   * Checks if the bestia knows this attack.
   */
  fun knowsAttack(entity: Entity, attackId: Long): Boolean {
    val attack = attackDao.findOneOrThrow(attackId)

    return knowsAttack(entity, attack)
  }

  /**
   * This method checks if the bestia has learned the attack and can use it.
   * In order to do so it uses the [AttackListComponent]. If the entity
   * does not own this component false is returned.
   *
   * @param entity
   * @param attack
   * @return TRUE if the entity knows the attack FALSE otherwise.
   */
  fun knowsAttack(entity: Entity, attack: Attack): Boolean {

    return entity.tryGetComponent(AttackListComponent::class.java)
        ?.knownAttacks?.contains(attack.id) ?: false
  }

  /**
   * Checks if the given entity is able to cast the attack (it knows it) and
   * also if the current mana is enough to use this skill. This has to take
   * into account any mana cost reducing status effects.
   *
   * @return TRUE of the entity can now use this skill/attack.
   */
  fun canUseAttack(attacker: Entity, attackId: Long): Boolean {
    val attack = attackDao.findOneOrThrow(attackId)
    return hasAllNeededBattleComponents(attacker) &&
        knowsAttack(attacker, attack) &&
        hasManaForAttack(attacker, attack) &&
        hasLineOfSightIfRequired(attacker, attack) &&
        hasRequiredItemsForAttack(attacker, attack) &&
        hasAttackRequirements(attacker, attack)
  }

  private fun hasLineOfSightIfRequired(entity: Entity, attack: Attack): Boolean {
    if (!attack.needsLineOfSight) {
      return true
    }

    // FIXME Check line of sight against target position via the map service.
    return true
  }

  private fun hasRequiredItemsForAttack(entity: Entity, attack: Attack): Boolean {
    // FIXME Implement
    return true
  }

  /**
   * Some attacks might have over constrains like they work only if a certain debuff
   * is applied etc. This must usually checked via the attack script.
   */
  private fun hasAttackRequirements(entity: Entity, attack: Attack): Boolean {
    // FIXME Implement
    return true
  }

  private fun hasManaForAttack(entity: Entity, attack: Attack): Boolean {
    // TODO Take Equipment and Status altering effects into account
    val currentMana = entity.tryGetComponent(ConditionComponent::class.java)?.conditionValues?.currentMana ?: 0
    return currentMana >= attack.manaCost
  }

  private fun hasAllNeededBattleComponents(entity: Entity): Boolean {
    return entity.tryGetComponent(PositionComponent::class.java) != null
        && entity.tryGetComponent(StatusComponent::class.java) != null
        && entity.tryGetComponent(LevelComponent::class.java) != null
  }
}
