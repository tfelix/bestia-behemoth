package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.AttackListComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.model.battle.AttackRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.battle.Attack
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
   * Teaches the given entity the given attack via its ID. In order for the
   * entity to learn the attack it must have a [StatusComponent] as well
   * as a [PositionComponent]. If it has not a
   * [AttackListComponent] this component will be added.
   */
  fun learnAttack(entity: Entity, attackId: Int) {
    LOG.debug("Entity {} learns attack {}.")
    // TODO Implementieren
  }

  /**
   * Checks if the bestia knows this attack.
   */
  fun knowsAttack(entity: Entity, attackId: Int): Boolean {
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
  fun canUseAttack(attacker: Entity, attackId: Int): Boolean {

    // TODO Check line of sight.

    val attack = attackDao.findOneOrThrow(attackId)
    return hasAllBattleComponents(attacker) &&
        knowsAttack(attacker, attack) &&
        hasManaForAttack(attacker, attack)
  }

  private fun hasManaForAttack(entity: Entity, attack: Attack): Boolean {
    // TODO Take Equipment and Status altering effects into account
    val currentMana = entity.tryGetComponent(StatusComponent::class.java)?.conditionValues?.currentMana ?: 0
    return currentMana >= attack.manaCost
  }

  private fun hasAllBattleComponents(entity: Entity): Boolean {
    return entity.tryGetComponent(PositionComponent::class.java) != null
        && entity.tryGetComponent(StatusComponent::class.java) != null
        && entity.tryGetComponent(LevelComponent::class.java) != null
  }
}
