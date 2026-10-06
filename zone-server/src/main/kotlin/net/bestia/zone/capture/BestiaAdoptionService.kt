package net.bestia.zone.capture

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.OwnedBestiaPolicyViolationException
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.bestia.OwnedBestiasPublisher
import net.bestia.zone.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Writes a caught bestia to its new master and puts it back into the world as theirs. Off the tick, because
 * that takes the database.
 */
@Component
class BestiaAdoptionService(
  private val bestiaCatalogue: BestiaCatalogue,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val playerBestiaCreateOperation: PlayerBestiaCreateOperation,
  private val bestiaEntitySpawner: BestiaEntitySpawner,
  private val ownedBestiasPublisher: OwnedBestiasPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val worldView: WorldView,
) {

  /** Published from the tick, so it only hands the work on. */
  @EventListener
  fun onCaptured(event: BestiaCapturedEvent) {
    asyncJobExecutor.submit(key = event.ownerAccountId) {
      adopt(event.ownerAccountId, event.masterId, bestiaCatalogue.byId(event.speciesId), event.at)
    }
  }

  private fun adopt(accountId: AccountId, masterId: Long, species: Bestia, at: Vec3L) {
    try {
      playerBestiaCreateOperation.createAndSpawn(
        masterId = masterId,
        playerBestiaCreateData = PlayerBestiaCreateOperation.PlayerBestiaCreateData(
          bestiaIdentifier = species.identifier,
          spawnPosition = at,
          level = species.level
        )
      )
    } catch (e: OwnedBestiaPolicyViolationException) {
      LOG.info { "Master $masterId has no free slot for the ${species.identifier} it caught: ${e.message}" }
      release(species, at)
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.BESTIA_SLOTS_FULL))
      return
    } catch (e: Exception) {
      LOG.error(e) { "Could not hand the caught ${species.identifier} to master $masterId; releasing it" }
      release(species, at)
      return
    }

    ownedBestiasPublisher.publish(accountId)
  }

  /** Puts the creature back as a wild one, so a failed hand-over loses nothing. */
  private fun release(species: Bestia, at: Vec3L) {
    worldView.read { bestiaEntitySpawner.spawnMob(this, species.id, at) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
