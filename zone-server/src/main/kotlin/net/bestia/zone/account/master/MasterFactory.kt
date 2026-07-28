package net.bestia.zone.account.master

import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.WorldService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.awt.Color

/**
 * Creates an entity with all the required component from a master db entity.
 */
@Component
class MasterFactory(
  private val accountRepository: AccountRepository,
  private val masterRepository: MasterRepository,
  private val worldService: WorldService
) {

  class CreateMasterData(
    val name: String,
    val hairColor: Color,
    val skinColor: Color,
    val hair: Hairstyle,
    val face: Face,
    val body: BodyType
  )

  /**
   * Creates a master for a managed Account entity. Does not save the Account.
   */
  fun create(
    account: Account,
    createMasterData: CreateMasterData
  ): Master {
    // Validate name length
    if (createMasterData.name.isBlank() || createMasterData.name.length > 20) {
      throw InvalidMasterNameException()
    }

    // Check master count limit
    val maxSlots = Account.DEFAULT_MASTER_SLOT_COUNT + account.additionalMasterSlots
    if (account.master.size >= maxSlots) {
      throw MaxMastersReachedException()
    }

    val newMaster = Master(
      account = account,
      name = createMasterData.name,
      hairColor = createMasterData.hairColor,
      skinColor = createMasterData.skinColor,
      hair = createMasterData.hair,
      face = createMasterData.face,
      body = createMasterData.body
    )

    // The entity's own default is the origin, which is the corner of the map inside the drowned ocean margin.
    // Guarded on the world being loaded so that a test creating a master without a world still gets one, rather
    // than an exception from somewhere that has nothing to do with what it is testing.
    if (worldService.isLoaded) {
      newMaster.spawnPosition = worldService.defaultSpawn
      newMaster.currentPosition = newMaster.spawnPosition
    }

    try {
      account.master.add(newMaster)

      return masterRepository.save(newMaster)
    } catch (_: DataIntegrityViolationException) {
      throw MasterNameAlreadyTakenException()
    }
  }

  fun create(
    accountId: AccountId,
    createMaster: CreateMasterData
  ): Master {
    val account = accountRepository.findByIdOrThrow(accountId)
    return create(account, createMaster)
  }
}