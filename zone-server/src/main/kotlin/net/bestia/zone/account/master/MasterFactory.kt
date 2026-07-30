package net.bestia.zone.account.master

import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.MasterSpawnPointService
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
  private val masterSpawnPointService: MasterSpawnPointService,
  private val worldService: WorldService
) {

  class CreateMasterData(
    val name: String,
    val hairColor: Color,
    val skinColor: Color,
    val hair: Hairstyle,
    val face: Face,
    val body: BodyType,
    /**
     * Id of the [net.bestia.zone.world.MasterSpawnPoint] the player chose, or null to be placed at the
     * first candidate. Null is not "no opinion" arriving from a real client - the wire field is a
     * presence-less `uint32` where 0 means unset - it is the fallback for anything that creates a master
     * without going through the selection screen.
     */
    val spawnPointId: Int? = null
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
      // Asked for rather than read straight from the repository so that a create arriving before anyone has
      // listed the masters still finds candidates, instead of rejecting a perfectly valid id against an
      // empty table.
      val candidates = masterSpawnPointService.ensureComputed()

      val spawnPoint = when (val chosenId = createMasterData.spawnPointId) {
        null -> candidates.firstOrNull()
        else -> candidates.firstOrNull { it.id == chosenId.toLong() } ?: throw MasterInvalidSpawnPointException()
      }

      // Null only when the world has no standing settlements at all. `WorldService` refuses to boot a *new*
      // world in that state, so reaching here means a pre-existing world it warned about rather than
      // regenerated - and there is no longer any world-default position to fall back to. Better to refuse the
      // creation than to write a master a coordinate that means nothing.
      if (spawnPoint == null) {
        throw GeneralMasterException("The world offers no spawn point to place a new master at")
      }

      newMaster.spawnPosition = spawnPoint.position
      newMaster.homeSettlementName = spawnPoint.settlementName
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