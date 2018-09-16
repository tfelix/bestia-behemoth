package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.AttackListComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.entity.component.StatusComponent
import net.bestia.model.dao.AttackDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.Attack
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Provides all access to let entities learn attacks.
 *
 * @author Thomas Felix
 */
@Service
class AttackService(
        private val entityService: EntityService,
        private val attackDao: AttackDAO
) {

  /**
   * Teaches the given entity the given attack via its ID. In order for the
   * entity to learn the attack it must have a [StatusComponent] as well
   * as a [PositionComponent]. If it has not a
   * [AttackListComponent] this component will be added.
   *
   * @param entityId
   * The entity to learn the attack.
   * @param attackId
   * The attack to learn.
   */
  fun learnAttack(entityId: Long, attackId: Int) {
    LOG.debug("Entity {} learns attack {}.")
  }

  /**
   * Checks if the bestia knows this attack.
   */
  fun knowsAttack(entityId: Long, attackId: Int): Boolean {
    val attack = attackDao.findOneOrThrow(attackId)
    val attacker = entityService.getEntity(attackId.toLong())
    return knowsAttack(attacker, attack)
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

    val attacks = entityService.getComponent(entity, AttackListComponent::class.java)

    return if (!attacks.isPresent) {
      false
    } else attacks.get().knowsAttack(attack.id)

  }

  /**
   * Checks if the given entity is able to cast the attack (it knows it) and
   * also if the current mana is enough to use this skill. This has to take
   * into account any mana cost reducing status effects.
   *
   * @return TRUE of the entity can now use this skill/attack.
   */
  fun canUseAttack(attackerId: Long, attackId: Int): Boolean {

    // TODO Check line of sight.

    val attack = attackDao.findOneOrThrow(attackId)
    val attacker = entityService.getEntity(attackId.toLong())

    return hasAllBattleComponents(attacker) && knowsAttack(attacker, attack) && hasManaForAttack(attacker, attack)
  }

  private fun hasManaForAttack(entity: Entity, attack: Attack): Boolean {
    return false
  }

  private fun hasAllBattleComponents(entity: Entity?): Boolean {
    return if (entity == null) {
      false
    } else entityService.hasComponent(entity, PositionComponent::class.java)
            && entityService.hasComponent(entity, StatusComponent::class.java)
            && entityService.hasComponent(entity, LevelComponent::class.java)
  }
}
