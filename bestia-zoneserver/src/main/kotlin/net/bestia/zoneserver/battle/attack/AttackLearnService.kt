package net.bestia.zoneserver.battle.attack

import mu.KotlinLogging
import net.bestia.model.battle.*
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.AttackListComponent
import net.bestia.zoneserver.entity.component.MetadataComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Fetches the list of learnable attacks from the server.
 */
@Service
class AttackLearnService(
    private val bestiaAttackRepository: BestiaAttackRepository,
    private val playerAttackRepository: PlayerAttackRepository
) {

  /**
   * Returns a list of known attacks of this entity. It will first check if this
   * is a player entity and return the saved attacks from database and the mobs
   * default attacks.
   */
  fun getKnownAttacks(entity: Entity): List<KnownAttack> {
    val bestiaAttacks = getDefaultBestiaAttacks(entity)
        .map { KnownAttack(minLevel = it.minLevel, databaseName = it.attack.databaseName, attackId = it.attack.id) }

    val playerAttacks = getLearnedPlayerAttacks(entity)
        .map { KnownAttack(minLevel = it.minLevel, databaseName = it.attack.databaseName, attackId = it.attack.id) }

    return (bestiaAttacks + playerAttacks).sortedBy { it.minLevel }
  }

  private fun getDefaultBestiaAttacks(entity: Entity): List<BestiaAttack> {
    val bestiaId = entity.tryGetComponent(MetadataComponent::class.java)
        ?.tryGetAsLong(MetadataComponent.MOB_BESTIA_ID)
        ?: return emptyList()

    return bestiaAttackRepository.getAllAttacksForBestia(bestiaId)
  }

  private fun getLearnedPlayerAttacks(entity: Entity): List<PlayerBestiaAttack> {
    val playerBestiaId = entity.tryGetComponent(MetadataComponent::class.java)
        ?.let { it.data[MetadataComponent.MOB_PLAYER_BESTIA_ID] }
        ?.toLong()
        ?: return emptyList()

    return playerAttackRepository.getAllAttacksForBestia(playerBestiaId)
  }

  /**
   * Teaches the given entity the given attack via its ID. In order for the
   * entity to learn the attack it must have a [StatusComponent] as well
   * as a [PositionComponent].
   * If it has not a [AttackListComponent] this component will be added.
   */
  fun learnAttack(entity: Entity, attackId: Long): AttackListComponent {
    LOG.debug("Entity $entity learns attack $attackId")
    // TODO If it is a player entity persist learned attack
    // otherwise just put it into component.
    return entity.getComponent(AttackListComponent::class.java)
  }
}