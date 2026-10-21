package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.IdleStance
import net.bestia.zone.master.bestia.BestiaAiConfigService
import net.bestia.zone.message.AccountTaskExecutor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * A standing order from the companion app. It takes effect at once, like one given in the game, and runs in the
 * account's inbox so it is ordered with whatever that account's game client sends at the same moment.
 */
@Service
class StanceCommand(
  private val owned: OwnedMasters,
  private val playerBestias: PlayerBestiaRepository,
  private val aiConfigs: BestiaAiConfigService,
  private val inbox: AccountTaskExecutor,
) {

  fun apply(correlationId: String, accountId: Long, query: ZoneQuery.SetStance): BusReply {
    val stance = IdleStance.fromNameOrNull(query.stance)
      ?: return BusReply.of(correlationId, Status.INVALID, "Unknown stance '${query.stance}'")

    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        applyOwned(correlationId, accountId, query, stance)
      } catch (e: Exception) {
        LOG.error(e) { "Stance for bestia ${query.playerBestiaId} of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private fun applyOwned(correlationId: String, accountId: Long, query: ZoneQuery.SetStance, stance: IdleStance): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    val playerBestia = playerBestias.findByIdOrNull(query.playerBestiaId)
    if (playerBestia == null || playerBestia.master.id != query.masterId) {
      return BusReply.of(correlationId, Status.NOT_FOUND)
    }

    val stored = aiConfigs.apply(query.playerBestiaId, AiConfig(stance, query.aggression))
      ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    return BusReply.ok(correlationId, ZoneAnswer.Stance(query.playerBestiaId, stored.stance.name, stored.aggression))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
