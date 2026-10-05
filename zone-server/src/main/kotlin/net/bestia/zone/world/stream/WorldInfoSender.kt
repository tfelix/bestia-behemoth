package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Tells a freshly authenticated connection what world it is in, and cleans up after it when it leaves.
 *
 * Sent on connect rather than on master selection, because none of it depends on having an entity: a client
 * needs the world's extent to fold an address across a seam, and getting it out of the way early means the
 * first manifest can be acted on the moment it arrives.
 */
@Component
class WorldInfoSender(
  private val worldService: WorldService,
  private val chunkService: ChunkService,
  private val inbox: ChunkStreamInbox,
  private val outMessageProcessor: OutMessageProcessor,
  private val bestiaClock: BestiaClock,
  private val settings: ChunkStreamConfig
) {

  /**
   * A body kept in the world after a disconnect is still streamed to, so the server believes chunks were
   * offered that the new connection never received. Starting over makes it announce them again.
   */
  @EventListener
  fun handleAccountConnected(event: AccountConnectedEvent) {
    inbox.offerReset(event.accountId)

    if (!chunkService.isReady) {
      LOG.warn { "Account ${event.accountId} connected before the world was generated; sending no world info" }
      return
    }

    outMessageProcessor.sendToPlayer(
      event.accountId,
      WorldInfoSMSG.of(
        worldService.record,
        bestiaClock.now(),
        bestiaClock.speedFactor,
        settings.viewRadiusChunks
      )
    )

    LOG.debug { "Sent world info to account ${event.accountId}" }
  }

  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    inbox.offerReset(event.accountId)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
