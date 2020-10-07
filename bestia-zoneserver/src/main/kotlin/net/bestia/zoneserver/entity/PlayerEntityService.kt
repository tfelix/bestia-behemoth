package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.Account.Companion.NUM_BESTIA_SLOTS
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.actor.client.ClientInfoResponse
import net.bestia.zoneserver.entity.component.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException
import javax.transaction.Transactional

private val LOG = KotlinLogging.logger { }

/**
 * This service manages the entities which are controlled by a player.
 *
 * @author Thomas Felix
 */
@Service
@Transactional
class PlayerEntityService(
    private val playerBestiaRepository: PlayerBestiaRepository,
    private val accountRepository: AccountRepository
) {

  private fun getAccountOrThrow(accountId: Long): Account {
    return accountRepository.findByIdOrNull(accountId)
        ?: throw IllegalArgumentException("Account with id '$accountId' was not found")
  }

  /**
   * Returns the active entity id for the given account.
   *
   * @param accountId The account id.
   * @return The active entity id of this account or null.
   */
  fun getActivePlayerEntityId(accountId: Long): Long? {
    val account = getAccountOrThrow(accountId)

    return account.activeBestia?.entityId
  }

  fun setDefaultActivePlayerBestia(accountId: Long) {
    LOG.trace { "setDefaultActivePlayerBestia($accountId)" }
    val account = getAccountOrThrow(accountId)
    val master = account.masterBestia!!

    if (master.entityId == 0L) {
      error("Master Bestia was not yet spawned and has no entity")
    }

    account.activeBestia = master

    accountRepository.save(account)
  }

  fun getClientInfo(accountId: Long): ClientInfoResponse {
    val account = getAccountOrThrow(accountId)

    return ClientInfoResponse(
        bestiaSlotCount = NUM_BESTIA_SLOTS + account.additionalBestiaSlots,
        masterBestiaEntityId = account.masterBestia!!.entityId,
        ownedBestiaEntityIds = account.playerBestias.map { it.entityId }
    )
  }

  /**
   * This method extracts all variable and important data from the player
   * entity and persists them back into the database.
   */
  fun save(playerEntity: Entity) {
    val metaComp = playerEntity.getComponent(MetadataComponent::class.java)
    val playerBestiaId = metaComp.tryGetAsLong(MetadataComponent.MOB_PLAYER_BESTIA_ID)
        ?: throw IllegalArgumentException("MOB_PLAYER_BESTIA_ID was not present")
    val playerBestia = playerBestiaRepository.findOneOrThrow(playerBestiaId)

    // Current status values (HP/Mana)
    val conditionComp = playerEntity.getComponent(ConditionComponent::class.java)
    playerBestia.conditionValues = conditionComp.conditionValues

    // Current position.
    val posComp = playerEntity.getComponent(PositionComponent::class.java)
    playerBestia.currentPosition = posComp.position

    // Level and exp.
    val levelComp = playerEntity.getComponent(LevelComponent::class.java)
    playerBestia.exp = levelComp.exp
    playerBestia.level = levelComp.level

    playerBestiaRepository.save(playerBestia)
  }
}
