package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.account.master.status.EffortValueCostCalculator
import net.bestia.zone.account.master.status.StatusAttribute
import net.bestia.zone.account.master.status.setEffortValue
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.cartography.CartographyConfig
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.MasterSpawnPoint
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
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
  private val effortValueCostCalculator: EffortValueCostCalculator,
  private val chartService: ChartService,
  private val cartographyConfig: CartographyConfig,
  private val worldService: WorldService,
) {

  class CreateMasterData(
    val name: String,
    val hairColor: Color,
    val skinColor: Color,
    val hair: Hairstyle,
    val face: Face,
    val body: BodyType,
    val spawnPointId: Int,
    /**
     * Starting effort value per attribute. Defaults to an even spread at the creation cap, which is
     * exactly what the budget buys - so fixtures and tests that don't care about the build get a
     * valid distribution without having to compute one.
     */
    val effortValues: Map<StatusAttribute, Int> = MasterFactory.evenlySpreadEffortValues()
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

    validateEffortValues(createMasterData.effortValues)

    val newMaster = Master(
      account = account,
      name = createMasterData.name,
      hairColor = createMasterData.hairColor,
      skinColor = createMasterData.skinColor,
      hair = createMasterData.hair,
      face = createMasterData.face,
      body = createMasterData.body
    )

    createMasterData.effortValues.forEach { (attribute, value) -> newMaster.setEffortValue(attribute, value) }

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
    grantStarterChart(savedMaster, spawnPoint)

    return savedMaster
  }

  /**
   * Puts a chart of the home settlement in a new master's pack.
   *
   * Charts are the *only* source of map knowledge - there is no permanent per-master atlas - so without this a
   * fresh master's map and minimap are blank, which reads as a broken window rather than as fog. How much
   * ground it covers, and why it is not a token amount, is [CartographyConfig.starterChartRadiusMetres].
   *
   * Failure is logged and swallowed. A missing item template is a broken deployment, and refusing to create the
   * master over it would turn a blank map into an account nobody can play.
   */
  private fun grantStarterChart(master: Master, spawnPoint: MasterSpawnPoint) {
    val voxelSize = worldService.config.voxelSize
    val result = chartService.grantStarterChart(
      masterId = master.id,
      centreX = spawnPoint.position.x * voxelSize,
      centreY = spawnPoint.position.y * voxelSize,
      radiusMetres = cartographyConfig.starterChartRadiusMetres
    )

    when (result) {
      is ChartService.Result.Ok -> LOG.info {
        "Master ${master.id} starts with a chart of ${result.cells} cells around ${spawnPoint.settlementName}"
      }

      is ChartService.Result.Refused -> LOG.error {
        "Master ${master.id} could not be given a starter chart (${result.error}); their map will be blank"
      }
    }
  }

  /**
   * Refuses any starting build that isn't one the creation screen could have produced: all six
   * attributes named, none below the floor, and the whole budget spent to the last point.
   *
   * No upper bound per attribute - the budget is the ceiling (see
   * [EffortValueCostCalculator.BALANCED_EFFORT_VALUE] for why an explicit cap would leave exactly one
   * legal distribution).
   *
   * Reported as a plain [GeneralMasterException] rather than getting its own error code on purpose.
   * The creation screen keeps Create disabled until the distribution is valid, so nothing a player
   * does can land here - only a broken or hand-crafted client, which has no message to read. See
   * `.claude/skills/error-messages/SKILL.md`.
   */
  private fun validateEffortValues(effortValues: Map<StatusAttribute, Int>) {
    val missing = StatusAttribute.entries.filterNot { effortValues.containsKey(it) }
    if (missing.isNotEmpty()) {
      throw GeneralMasterException("Effort value distribution is missing $missing")
    }

    val floor = EffortValueCostCalculator.MIN_EFFORT_VALUE_AT_CREATION
    val tooLow = effortValues.filterValues { it < floor }
    if (tooLow.isNotEmpty()) {
      throw GeneralMasterException("Effort values $tooLow are below the minimum of $floor")
    }

    val spent = effortValues.values.sumOf { effortValueCostCalculator.cumulativeCost(it) }
    if (spent != EffortValueCostCalculator.CREATION_EFFORT_POINTS) {
      throw GeneralMasterException(
        "Effort value distribution $effortValues costs $spent points, " +
          "but exactly ${EffortValueCostCalculator.CREATION_EFFORT_POINTS} must be spent"
      )
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Every attribute at [EffortValueCostCalculator.BALANCED_EFFORT_VALUE], which costs exactly
     * [EffortValueCostCalculator.CREATION_EFFORT_POINTS] - a valid, opinion-free starting build for
     * fixtures that don't care what the master's stats are.
     */
    fun evenlySpreadEffortValues(): Map<StatusAttribute, Int> =
      StatusAttribute.entries.associateWith { EffortValueCostCalculator.BALANCED_EFFORT_VALUE }
  }
}