package net.bestia.zone.account.master

import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.MasterSpawnPointService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.awt.Color

/**
 * Builds and persists a new [Master] row for a managed [Account] from what the player filled in on the
 * creation screen, resolving the [net.bestia.zone.world.MasterSpawnPoint] they picked into a world position.
 *
 * The database half only - this never touches the ECS. The master becomes a live entity later, when the
 * player selects it and [MasterEntitySpawner] materializes the persisted row into the world.
 *
 * It can nevertheless write *entity* state, because the master's [Master.entityId] is stamped here rather
 * than at spawn: the id exists from creation onwards, so per-entity storage keyed by it - currently
 * persisted status effects - can be seeded for a master nobody has ever selected.
 */
@Component
class MasterFactory(
  private val accountRepository: AccountRepository,
  private val masterRepository: MasterRepository,
  private val masterSpawnPointService: MasterSpawnPointService,
  private val entityIdGenerator: EntityIdGenerator,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
) {

  class CreateMasterData(
    val name: String,
    val hairColor: Color,
    val skinColor: Color,
    val hair: Hairstyle,
    val face: Face,
    val body: BodyType,
    val spawnPointId: Int
  )

  /**
   * Creates a master for a managed Account entity. Does not save the Account.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun create(
    accountId: AccountId,
    createMasterData: CreateMasterData
  ): Master {
    val account = accountRepository.findByIdOrThrow(accountId)

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

    // Asked for rather than read straight from the repository so that a create arriving before anyone has
    // listed the masters still finds candidates, instead of rejecting a perfectly valid id against an
    // empty table.
    val candidates = masterSpawnPointService.ensureComputed()

    // Empty only when the world has no standing settlements at all. `WorldService` refuses to boot a *new*
    // world in that state, so reaching here means a pre-existing world it warned about rather than
    // regenerated. Reported apart from a bad id because nothing the player picks could have worked.
    if (candidates.isEmpty()) {
      throw GeneralMasterException("The world offers no spawn point to place a new master at")
    }

    val spawnPoint = candidates.firstOrNull { it.id == createMasterData.spawnPointId.toLong() }
      ?: throw MasterInvalidSpawnPointException()

    newMaster.spawnPosition = spawnPoint.position
    newMaster.homeSettlementName = spawnPoint.settlementName
    newMaster.currentPosition = newMaster.spawnPosition

    // Taken from the zone's one shared generator, so this id can never collide with one the ECS hands
    // out at runtime. It is the master's entity id for the rest of its life, across every spawn.
    newMaster.entityId = entityIdGenerator.nextId()

    val savedMaster = try {
      account.master.add(newMaster)

      masterRepository.save(newMaster)
    } catch (_: DataIntegrityViolationException) {
      // Surfaces synchronously: Master's id is IDENTITY, so save() has to run the INSERT to get the id
      // rather than deferring it to flush, which is what keeps this catch able to name the actual cause.
      throw MasterNameAlreadyTakenException()
    }

    // Seeded now rather than applied in MasterEntitySpawner, which is what makes the greeting a
    // once-ever event: the spawner replays whatever is stored, and MasterIntroMarker deletes itself
    // after firing, so the second login finds nothing to replay.
    statusEffectPersistenceService.seed(savedMaster.entityId, StatusEffectId.MASTER_INTRO_MARKER)

    return savedMaster
  }
}