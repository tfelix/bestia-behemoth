package net.bestia.api.auth

import io.lettuce.core.RedisClient
import jakarta.annotation.PreDestroy
import net.bestia.api.live.LiveSessions
import net.bestia.bus.BusKeys
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamReader
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Reads the login server's kicks, so a ban or a GM kick also locks the account out of the companion app at once.
 * Every instance follows the whole stream, and on start replays the kicks it still has to remember.
 */
@Component
class KickOrderListener(
  private val busClient: RedisClient,
  private val kickedAccounts: KickedAccounts,
  private val sessions: LiveSessions,
) {

  @Volatile
  private var reader: ValkeyStreamReader<KickOrder>? = null

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    val oldestToRemember = Instant.now().minus(kickedAccounts.memory).toEpochMilli()
    val kicks = ValkeyStreamReader(busClient, BusKeys.KICKS, KickOrder::class.java)
    kicks.tail("kicked-accounts", afterId = "$oldestToRemember-0") { handle(it.payload) }
    reader = kicks
  }

  fun handle(order: KickOrder) {
    val kickedAt = Instant.ofEpochMilli(order.kickedAtEpochMs)
    kickedAccounts.remember(order.accountId, kickedAt)
    sessions.closeOpenedBefore(order.accountId, kickedAt)
  }

  @PreDestroy
  fun stop() {
    reader?.close()
  }
}
