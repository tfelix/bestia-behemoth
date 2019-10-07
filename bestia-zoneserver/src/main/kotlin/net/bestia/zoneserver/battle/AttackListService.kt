package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.BestiaAttackRepository
import net.bestia.model.battle.PlayerAttackRepository
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.TagComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Fetches the list of learnable attacks from the server.
 */
@Service
class AttackListService(
    private val bestiaAttackRepository: BestiaAttackRepository,
    private val playerAttackRepository: PlayerAttackRepository
) {

  fun getLearnableAttacks(entity: Entity): List<LearnedAttack> {
    val bestiaAttacks = if (isMobEntity(entity)) {
      val bestiaId = entity.tryGetComponent(MetaDataComponent::class.java)
          ?.let { it.data[MetaDataComponent.MOB_BESTIA_ID] }
          ?.toLong()
      bestiaId?.let { bestiaAttackRepository.getAllAttacksForBestia(bestiaId) }
          ?: emptyList()
    } else {
      emptyList()
    }.map { LearnedAttack(minLevel = it.minLevel, attack = it.attack) }

    val playerAttacks = if (isPlayerEntity(entity)) {
      val pbId = entity.tryGetComponent(PlayerComponent::class.java)
          ?.playerBestiaId
      pbId?.let { playerAttackRepository.getAllAttacksForBestia(pbId) }
          ?: emptyList()
    } else {
      emptyList()
    }.map { LearnedAttack(minLevel = it.minLevel, attack = it.attack) }

    return (bestiaAttacks + playerAttacks).sortedBy { it.minLevel }
  }

  private fun isPlayerEntity(entity: Entity): Boolean {
    val tagComp = entity.tryGetComponent(TagComponent::class.java)
        ?: return false
    return tagComp.tags.contains(TagComponent.PLAYER)
  }

  private fun isMobEntity(entity: Entity): Boolean {
    val tagComp = entity.tryGetComponent(TagComponent::class.java)
        ?: return false
    return tagComp.tags.contains(TagComponent.MOB)
  }

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
}