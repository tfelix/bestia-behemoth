package net.bestia.zone.capture

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.account.OwnedBestiaPolicyViolationException
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.compendium.CompendiumRecorder
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.master.bestia.OwnedBestiasPublisher
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Writes a caught bestia to its new master and puts it back into the world as theirs. Off the tick, because
 * that takes the database.
 */
@Component
class BestiaAdoptionService(
  private val bestiaCatalogue: BestiaCatalogue,
  private val compendiumRecorder: CompendiumRecorder,
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
    // Recorded even when no slot is free, not even in storage, and the creature is released again: it was still caught.
    compendiumRecorder.caught(event.ownerAccountId, event.masterId, event.speciesId)

    asyncJobExecutor.submit(key = event.ownerAccountId) {
      adopt(event, bestiaCatalogue.byId(event.speciesId))
    }
  }

  private fun adopt(event: BestiaCapturedEvent, species: Bestia) {
    val accountId = event.ownerAccountId
    val masterId = event.masterId
    val adopted = try {
      playerBestiaCreateOperation.createAndSpawn(
        masterId = masterId,
        playerBestiaCreateData = PlayerBestiaCreateOperation.PlayerBestiaCreateData(
          bestiaIdentifier = species.identifier,
          spawnPosition = event.at,
          level = event.level
        )
      )
    } catch (e: OwnedBestiaPolicyViolationException) {
      LOG.info { "Master $masterId has no free slot for the ${species.identifier} it caught: ${e.message}" }
      release(event, species)
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.BESTIA_SLOTS_FULL))
      return
    } catch (e: Exception) {
      LOG.error(e) { "Could not hand the caught ${species.identifier} to master $masterId; releasing it" }
      release(event, species)
      return
    }

    ownedBestiasPublisher.publish(accountId)
    if (adopted.isStored) {
      outMessageProcessor.sendToPlayer(accountId, OperationSuccessSMSG(OpSuccess.BESTIA_STORED_ON_ARRIVAL))
    }
  }

  /** Puts the creature back as a wild one, so a failed hand-over loses nothing. */
  private fun release(event: BestiaCapturedEvent, species: Bestia) {
    if (event.denBringsItBack) return

    worldView.read { bestiaEntitySpawner.spawnMob(this, species.id, event.at, level = event.level) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
