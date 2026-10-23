package net.bestia.zone.account.authentication

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import jakarta.annotation.PreDestroy
import net.bestia.bus.BusKeys
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamReader
import net.bestia.zone.bus.BusProperties
import net.bestia.zone.message.ConnectionTerminator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Carries out the login server's kicks. They are read from [BusKeys.KICKS] in a consumer group of this node's
 * own, so every node sees every kick, and a node that was down works through the ones appended meanwhile.
 *
 * A kick is remembered even when the account does not play here: it may be on its way, with a token issued
 * before the kick. Only a kick made after this node started closes a connection. An older one is being read on
 * catching up, and any connection here is younger than it, so it belongs to a player who logged in again.
 */
@Component
@ConditionalOnProperty(prefix = "bus", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class KickOrderListener(
  private val busClient: RedisClient,
  private val properties: BusProperties,
  private val kickedAccounts: KickedAccounts,
  private val connections: ConnectionTerminator,
  private val metrics: AuthMetrics = AuthMetrics(),
) {

  private val startedAt = System.currentTimeMillis()

  @Volatile
  private var reader: ValkeyStreamReader<KickOrder>? = null

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    val kicks = ValkeyStreamReader(busClient, BusKeys.KICKS, KickOrder::class.java)
    kicks.consume(BusKeys.kickGroup(properties.nodeId), properties.nodeId) { handle(it.payload) }
    reader = kicks
  }

  fun handle(order: KickOrder) {
    kickedAccounts.remember(order.accountId, order.kickedAtEpochMs)
    metrics.kick(AuthMetrics.Kick.RECEIVED)

    if (order.kickedAtEpochMs < startedAt) {
      metrics.kick(AuthMetrics.Kick.PREDATES_NODE)
      LOG.info { "Kick of account ${order.accountId} predates this node; its connection, if any, is newer" }
      return
    }
    val closed = connections.disconnect(order.accountId, order.reason)
    metrics.kick(if (closed) AuthMetrics.Kick.CLOSED else AuthMetrics.Kick.NO_CONNECTION)
  }

  @PreDestroy
  fun stop() {
    reader?.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
