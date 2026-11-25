package net.bestia.zone.etching

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.crafting.CraftBonusService
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.etching.persistence.EtchingJob
import net.bestia.zone.etching.persistence.EtchingJobRepository
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.stat.ItemRoll
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import net.bestia.zone.world.WorldService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import kotlin.random.Random

/**
 * Runic Etching on a Bestia locked into a Carving Table. It takes the craft duration of the Bestia's level, hours
 * for a strong one, so it runs on its own once it started: the Artificer may leave, and a restart does not reset it.
 * The Essence Gem goes to the Bestia's owner by post. Calling it off sends the gem back to the Artificer by post.
 */
@Service
class EtchingJobs(
  private val recipes: RecipeRegistry,
  private val bonuses: CraftBonusService,
  private val crafters: Crafters,
  private val rules: EtchingRules,
  private val playerBestias: PlayerBestiaRepository,
  private val jobs: EtchingJobRepository,
  private val inventoryService: InventoryService,
  private val itemTemplates: ItemTemplateRegistry,
  private val itemStats: ItemStatCatalog,
  private val loss: PlayerBestiaLoss,
  private val parcels: ParcelService,
  private val letters: LetterService,
  private val worldService: WorldService,
  private val worldView: WorldView,
  private val asyncJobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val out: OutMessageProcessor,
  private val clock: Clock = Clock.systemUTC(),
  private val random: Random = Random.Default,
) {

  /** What the tick knows of the Artificer when an etching starts; the roll is settled from it. */
  private class Artificer(val accountId: Long, val masterId: Long, val name: String, val entityId: EntityId)

  /**
   * The master at [artificerId] starts to etch the Bestia [playerBestiaId] locked into the table [structureId]. The
   * gem leaves the bag now; the rest is checked off the tick, and a refusal gives the gem back.
   */
  fun start(world: World, artificerId: EntityId, structureId: Long, recipeId: Long, playerBestiaId: PlayerBestiaId): OpError? {
    val recipe = recipes.of(recipeId)?.takeIf { it.etches } ?: return OpError.CRAFT_NOT_POSSIBLE
    val crafter = crafters.of(world, artificerId, artificerId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val known = crafter.skills
    if (known?.knowsSkill(recipe.requiredSkillId, recipe.requiredSkillLevel) != true) return OpError.CRAFT_NOT_POSSIBLE

    val accountId = world.get(artificerId, Account::class)?.accountId ?: return OpError.CRAFT_NOT_POSSIBLE
    val gem = world.get(artificerId, Inventory::class)?.takeAcrossPiles(recipe.etchedGemId, 1)
      ?: return OpError.CRAFT_MISSING_MATERIALS

    val attributes = world.get(artificerId, CombatStats::class)?.values?.attributes
    val artificer = Artificer(accountId, crafter.master.masterId, crafter.master.name, artificerId)
    val chanceAt = { level: Int -> bonuses.successChance(known, recipe, attributes, level) }
    val secondsAt = { level: Int -> bonuses.craftSeconds(known, recipe, level) }

    asyncJobs.submit(artificer.masterId) {
      val refusal = begin(artificer, structureId, recipe, playerBestiaId, chanceAt, secondsAt)
      if (refusal != null) {
        worldView.modify(artificerId) { id -> gem.forEach { get(id, Inventory::class)?.addItem(it) } }
        out.sendToPlayer(artificer.accountId, OperationErrorSMSG(refusal))
      }
    }

    return null
  }

  /** The Artificer calls off their own etching of [playerBestiaId]. Off the tick. */
  fun callOff(artificerMasterId: Long, playerBestiaId: PlayerBestiaId) {
    transactions.executeWithoutResult {
      val job = jobs.findByIdOrNull(playerBestiaId)?.takeIf { it.artificerMasterId == artificerMasterId }
        ?: return@executeWithoutResult
      refund(job)
    }
  }

  /** Calls off the etching of [playerBestiaId] once it is no longer locked into the job's table. Off the tick. */
  fun callOffIfTakenOut(playerBestiaId: PlayerBestiaId) {
    transactions.executeWithoutResult {
      val job = jobs.findByIdOrNull(playerBestiaId) ?: return@executeWithoutResult
      if (playerBestias.findByIdOrNull(playerBestiaId)?.depositedAtStructureId != job.structureId) refund(job)
    }
  }

  /** The table [structureId] is gone, and every etching at it with it. Off the tick. */
  fun callOffAllAt(structureId: Long) {
    transactions.executeWithoutResult { jobs.findAllByStructureId(structureId).forEach(::refund) }
  }

  /** The etchings whose time is up, with the master whose job key each one resolves on. */
  fun due(): List<Pair<PlayerBestiaId, Long>> {
    return transactions.execute {
      jobs.findAllByEndsAtLessThanEqual(clock.instant()).map { job ->
        job.playerBestiaId to (playerBestias.findByIdOrNull(job.playerBestiaId)?.master?.id ?: job.artificerMasterId)
      }
    }.orEmpty()
  }

  /** Rolls the etching of [playerBestiaId]. Off the tick, on the job key of the Bestia's owner. */
  fun resolve(playerBestiaId: PlayerBestiaId) {
    transactions.executeWithoutResult {
      val job = jobs.findByIdOrNull(playerBestiaId) ?: return@executeWithoutResult
      val bestia = playerBestias.findByIdForUpdate(playerBestiaId)
      if (bestia == null || bestia.depositedAtStructureId != job.structureId) return@executeWithoutResult refund(job)

      jobs.delete(job)
      if (random.nextFloat() <= job.chance) succeed(job, bestia) else fail(job, bestia)
    }
  }

  private fun begin(
    artificer: Artificer,
    structureId: Long,
    recipe: Recipe,
    playerBestiaId: PlayerBestiaId,
    chanceAt: (Int) -> Float,
    secondsAt: (Int) -> Float,
  ): OpError? {
    return transactions.execute {
      val bestia = playerBestias.findByIdForUpdate(playerBestiaId)?.takeIf { it.depositedAtStructureId == structureId }
        ?: return@execute OpError.REQUEST_REFUSED
      if (jobs.existsById(playerBestiaId)) return@execute OpError.CRAFT_ALREADY_IN_PROGRESS

      val tier = rules.tierOfGem(recipe.etchedGemId) ?: return@execute OpError.CRAFT_NOT_POSSIBLE
      if (!rules.fits(bestia.bestia.id, bestia.level, tier)) return@execute OpError.CRAFT_NOT_POSSIBLE
      if (!inventoryService.consumeAll(artificer.masterId, listOf(recipe.etchedGemId to 1))) {
        return@execute OpError.CRAFT_MISSING_MATERIALS
      }

      val seconds = secondsAt(bestia.level)
      jobs.save(
        EtchingJob(
          playerBestiaId = playerBestiaId,
          structureId = structureId,
          artificerMasterId = artificer.masterId,
          artificerName = artificer.name,
          gemItemId = recipe.etchedGemId,
          recipeId = recipe.id,
          chance = chanceAt(bestia.level),
          endsAt = clock.instant().plusMillis((seconds * 1000).toLong()),
        )
      )
      LOG.debug { "Master ${artificer.masterId} etches bestia $playerBestiaId at table $structureId for ${seconds}s" }
      null
    }
  }

  private fun succeed(job: EtchingJob, bestia: PlayerBestia) {
    val owner = bestia.master
    val tier = checkNotNull(rules.tierOfGem(job.gemItemId)) { "Etching job ${job.playerBestiaId} holds no cut gem" }
    val essenceGemId = checkNotNull(itemTemplates.idOf(ESSENCE_GEM)) { "No item '$ESSENCE_GEM'" }
    val roll = ItemRoll(emptyList(), sockets = 0, itemStats.itemLevelOf(essenceGemId), Essence(bestia.bestia.id, tier))
    val provenance = ItemProvenance(crafterName = job.artificerName, worldName = worldService.record.name)

    loss.loseStored(bestia.id)
    parcels.postInstant(owner.id, job.artificerName) { inventoryService.grantInto(it, essenceGemId, 1, roll, provenance) }
    LOG.info { "Master ${job.artificerMasterId} etched bestia ${bestia.id} of master ${owner.id} into a $tier gem" }
  }

  private fun fail(job: EtchingJob, bestia: PlayerBestia) {
    val name = bestia.name ?: "your Bestia"
    letters.postNotice(
      bestia.master.id,
      job.artificerName,
      "Runic Etching failed",
      "The gem shattered while ${job.artificerName} etched $name. It is unharmed and still waits at the Carving Table.",
    )
  }

  /** Sends the cut gem back to the Artificer and ends the etching. */
  private fun refund(job: EtchingJob) {
    jobs.delete(job)
    parcels.postInstant(job.artificerMasterId, SENDER_NAME) { inventoryService.grantInto(it, job.gemItemId, 1) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** The one item every etching makes; which Bestia and gem it holds is per copy. */
    const val ESSENCE_GEM = "essence_gem"

    const val SENDER_NAME = "Carving Table"
  }
}
