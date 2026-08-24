package net.bestia.zone.cartography

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Carries a resolved survey from the tick thread to the database and back.
 *
 * [checkBlank] is the odd one out: it runs at *activation*, from the skill script, and only looks. What the
 * survey costs is taken by `ChartService.mint` at the far end, so a cancelled cast pays nothing.
 *
 * Three things have to happen in three different places and this is what sequences them: the chart is written
 * relationally (off-tick, transactional), the live inventory the player is looking at is corrected (on the
 * world lock), and the outcome is reported (fire-and-forget). Splitting them is not a refinement - `zone-tick`
 * holds the world lock and may not do database work, and `ChartService` is transactional and may not touch the
 * world.
 *
 * ### Why the live inventory is corrected rather than left to resync
 *
 * The ECS [Inventory] is what the client sees and what carry capacity is computed from, and the row is what
 * survives a restart. A write that only did the second would show the player nothing until they logged out, and
 * would let them carry an unbounded number of charts in the meantime. `CraftingService.applyToTarget` says the
 * same thing about the same pair.
 *
 * Unlike the crafting path, this corrects the mirror *after* the write rather than before, and gets something
 * for it: the instance id is only known once the row exists, so the client learns the chart's real `uniqueId`
 * immediately instead of a placeholder zero it would have to see corrected on the next full send. A chart is
 * addressed by that id when it is merged or copied, so a placeholder would be a chart the player could hold but
 * not use.
 *
 * ### The world is a parameter, not an injected field
 *
 * `SkillExecutionService` depends on this bean, and the ECS world bean is assembled from every `System` -
 * including `CastingSystem`, which depends on `SkillExecutionService`. Injecting [WorldView] here therefore
 * closes a cycle Spring refuses to build, and the context fails at boot with nothing pointing at cartography.
 * `CraftingService` takes its world the same way for the same structural reason.
 *
 * A [WorldView] passed in is the same singleton an injected one would be - `World` implements the interface - so
 * nothing is lost but the constructor argument.
 */
@Service
class SurveyService(
  private val chartService: ChartService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
  private val outMessageProcessor: OutMessageProcessor,
  private val itemTemplates: ItemTemplateRegistry,
) {

  /**
   * Whether a survey may start at all: is there a master here, and is there anything to draw on.
   *
   * **Takes nothing.** The blank is spent by [ChartService.mint] when the channel finishes, so a cast that
   * is interrupted, cancelled or walked out of costs its caster nothing. This is the earlier of the two
   * checks that guard one removal, and it exists for the player rather than for correctness: without it the
   * refusal arrives five seconds late, after a cast bar that was always going to come to nothing.
   *
   * Reads the live [Inventory] rather than the container, because it runs on a message thread holding the
   * world lock, where a transaction may not go - and because the mirror is what the player is looking at. An
   * item promised to a trade is already out of it, so it cannot be counted here.
   *
   * @return the refusal to report, or null when the cast may start
   */
  fun checkBlank(world: World, entityId: EntityId): OpError? {
    if (world.get(entityId, Master::class) == null) {
      // Only a master holds charts, and only a master tree teaches CARTOGRAPHY - so an honest client never
      // sends this and the generic code plus a log is the whole answer.
      LOG.warn { "Entity $entityId is not a master and cannot survey" }
      return OpError.CHART_NOT_FOUND
    }

    val blankItemId = itemTemplates.idOf(ChartService.BLANK_IDENTIFIER)
    if (blankItemId == null) {
      // items.yml is imported at boot, so this is a broken deployment rather than anything a player did.
      LOG.error { "items.yml is missing '${ChartService.BLANK_IDENTIFIER}'; nobody can survey anything" }
      return OpError.CHART_NOT_FOUND
    }

    val holdsOne = world.get(entityId, Inventory::class)?.getItems()?.any { it.itemId == blankItemId } == true

    return if (holdsOne) null else OpError.CHART_NEEDS_BLANK
  }

  /**
   * Charts a disc around [centre] for [masterId], and reports the outcome to [accountId].
   *
   * Returns as soon as the job is queued. Keyed on the master, so two surveys by the same player cannot
   * interleave into a lost blank - the ordering guarantee `AsyncJobExecutor` documents for exactly this.
   *
   * @param centre the aimed-at point in **voxels**, as a skill target position always is
   */
  fun survey(
    world: WorldView,
    masterId: Long,
    accountId: Long?,
    entityId: EntityId,
    centre: Vec3L,
    radiusMetres: Double
  ) {
    val voxelSize = worldService.config.voxelSize
    val centreX = centre.x * voxelSize
    val centreY = centre.y * voxelSize

    asyncJobExecutor.submit(masterId) {
      when (val result = chartService.mint(masterId, centreX, centreY, radiusMetres)) {
        is ChartService.Result.Refused -> {
          LOG.debug { "Master $masterId could not chart: ${result.error}" }
          accountId?.let { outMessageProcessor.sendToPlayer(it, OperationErrorSMSG(result.error)) }
        }

        is ChartService.Result.Ok -> {
          LOG.debug {
            "Master $masterId charted ${result.cells} cells around $centreX, $centreY as instance ${result.uniqueId}"
          }
          applyToLiveInventory(world, entityId, result)
          accountId?.let { outMessageProcessor.sendToPlayer(it, OperationSuccessSMSG(OpSuccess.CHART_WRITTEN)) }
        }
      }
    }
  }

  /**
   * Mirrors a completed chart operation onto the live inventory component.
   *
   * Public because merging and copying need exactly this and produce exactly the same result type; the ECS half
   * of a chart operation is one thing whichever of the three wrote the row.
   *
   * Takes the world lock from off the tick, which is how a message handler reaches the world too - see
   * `GetInventoryHandler`. It blocks until the current tick lets go, which is why this is called from an async
   * worker and never from the tick itself.
   */
  fun applyToLiveInventory(
    world: WorldView,
    entityId: EntityId,
    result: ChartService.Result.Ok,
    removedUniqueIds: List<Long> = emptyList()
  ) {
    world.modify(entityId) { id ->
      val inventory = get(id, Inventory::class)
      if (inventory == null) {
        // Logged out between the survey resolving and the row being written. The chart is on the row, so it is
        // there on the next login; only the live mirror is missed.
        LOG.debug { "Entity $entityId has no inventory to show chart ${result.uniqueId} in" }
        return@modify
      }

      result.consumedBlankPaperItemId?.let { inventory.decItem(it.toInt()) }
      removedUniqueIds.forEach { inventory.removeByUniqueId(it) }

      // A chart never stacks - it carries per-instance state, which is the whole reason it has an instance -
      // and never wears, so it has no durability to report.
      inventory.addItem(
        Inventory.Item(
          itemId = result.item.id,
          amount = 1,
          weight = result.item.weight,
          uniqueId = result.uniqueId,
          stackable = false,
          durability = 0,
          maxDurability = 0
        )
      )
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
