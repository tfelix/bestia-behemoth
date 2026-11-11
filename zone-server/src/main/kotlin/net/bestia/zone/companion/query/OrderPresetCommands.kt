package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.master.bestia.OrderPresetService
import net.bestia.zone.message.AccountTaskExecutor
import org.springframework.stereotype.Service

/**
 * A master's order presets, from the companion app. Writes run in the account's inbox, like new orders, because they
 * change the orders of every bestia that follows the preset.
 */
@Service
class OrderPresetCommands(
  private val owned: OwnedMasters,
  private val presets: OrderPresetService,
  private val inbox: AccountTaskExecutor,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.OrderPresets): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    return BusReply.ok(correlationId, presetsOf(query.masterId))
  }

  fun save(correlationId: String, accountId: Long, query: ZoneQuery.SaveOrderPreset): BusReply {
    val orders = OrdersOnTheBus.ordersOf(query.sheet)
      .getOrElse { return BusReply.of(correlationId, Status.INVALID, it.message) }

    return inAccountInbox(correlationId, accountId) {
      owned.find(accountId, query.masterId) ?: return@inAccountInbox BusReply.of(correlationId, Status.NOT_FOUND)

      val saved = presets.save(query.masterId, query.presetId, query.name, orders)
      when (saved.refusal) {
        null -> BusReply.ok(correlationId, presetsOf(query.masterId))
        OrderPresetService.Refusal.NOT_FOUND -> BusReply.of(correlationId, Status.NOT_FOUND)
        else -> BusReply.of(correlationId, Status.INVALID, saved.refusal.name)
      }
    }
  }

  fun delete(correlationId: String, accountId: Long, query: ZoneQuery.DeleteOrderPreset): BusReply {
    return inAccountInbox(correlationId, accountId) {
      owned.find(accountId, query.masterId) ?: return@inAccountInbox BusReply.of(correlationId, Status.NOT_FOUND)

      if (presets.delete(query.masterId, query.presetId)) {
        BusReply.ok(correlationId, presetsOf(query.masterId))
      } else {
        BusReply.of(correlationId, Status.NOT_FOUND)
      }
    }
  }

  private fun presetsOf(masterId: Long) = ZoneAnswer.OrderPresets(
    presets.list(masterId).map { (preset, bestias) ->
      ZoneAnswer.OrderPresets.Preset(preset.id, preset.name, OrdersOnTheBus.sheetOf(preset.orders), bestias)
    }
  )

  private fun inAccountInbox(correlationId: String, accountId: Long, write: () -> BusReply): BusReply {
    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        write()
      } catch (e: Exception) {
        LOG.error(e) { "An order preset write of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
