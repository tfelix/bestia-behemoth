package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Tells a freshly authenticated connection what world it is in, and cleans up after it when it leaves.
 *
 * Sent on connect rather than on master selection, because none of it depends on having an entity: a client
 * needs the chunk dimensions to make sense of *any* payload, and getting them out of the way early means the
 * first manifest can be acted on the moment it arrives.
 */
@Component
class WorldInfoSender(
  private val worldService: WorldService,
  private val chunkService: ChunkService,
  private val subscriptions: ChunkSubscriptionService,
  private val inbox: ChunkStreamInbox,
  private val outMessageProcessor: OutMessageProcessor,
  private val settings: ChunkStreamConfig
) {

  @EventListener
  fun handleAccountConnected(event: AccountConnectedEvent) {
    if (!chunkService.isReady) {
      LOG.warn { "Account ${event.accountId} connected before the world was generated; sending no world info" }
      return
    }

    outMessageProcessor.sendToPlayer(
      event.accountId,
      WorldInfoSMSG.of(worldService.record, worldService.config, settings.viewRadiusChunks)
    )

    LOG.debug { "Sent world info to account ${event.accountId}" }
  }

  /**
   * Drops the connection's streaming state.
   *
   * [ChunkStreamSystem] would notice on its next tick anyway, because the account stops having an anchor -
   * but doing it here means a reconnect inside one tick cannot inherit the old connection's idea of what it
   * held, which would leave it receiving patches for chunks it never received.
   */
  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    subscriptions.forget(event.accountId)
    inbox.forget(event.accountId)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
