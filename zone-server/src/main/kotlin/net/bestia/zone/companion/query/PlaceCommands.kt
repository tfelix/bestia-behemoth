package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.master.bestia.NamedPlaceService
import net.bestia.zone.master.bestia.NamedPlaces
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import org.springframework.stereotype.Service

/**
 * A master's named places, from the companion app. Writes run in the account's inbox, because deleting a place
 * changes the orders of every bestia that names it.
 */
@Service
class PlaceCommands(
  private val owned: OwnedMasters,
  private val places: NamedPlaces,
  private val service: NamedPlaceService,
  private val structures: PlayerStructureRegistry,
  private val world: WorldView,
  private val plane: MapPlane,
  private val inbox: AccountTaskExecutor,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.BestiaPlaces): BusReply {
    owned.find(accountId, query.masterId) ?: return notFound(correlationId)

    return BusReply.ok(correlationId, placesOf(query.masterId))
  }

  fun saveArea(correlationId: String, accountId: Long, query: ZoneQuery.SaveAreaPlace): BusReply {
    val center = plane.positionAt(MapPoint(query.eastMetres, query.northMetres))

    return write(correlationId, accountId, query.masterId) {
      service.saveArea(query.masterId, query.placeId, query.name, center, query.radius).refusal?.name
    }
  }

  fun saveChest(correlationId: String, accountId: Long, query: ZoneQuery.SaveChestPlace): BusReply {
    return write(correlationId, accountId, query.masterId) {
      val chestAt = world.read {
        structures.of(query.chestStructureId)
          ?.takeIf { it.kind == StaticEntityKind.CHEST && it.ownerMasterId == query.masterId }
          ?.position
      }
      if (chestAt == null) {
        NamedPlaceService.Refusal.NOT_A_CHEST.name
      } else {
        service.saveChest(query.masterId, query.placeId, query.name, query.chestStructureId, chestAt).refusal?.name
      }
    }
  }

  fun delete(correlationId: String, accountId: Long, query: ZoneQuery.DeletePlace): BusReply {
    return write(correlationId, accountId, query.masterId) {
      if (service.delete(query.masterId, query.placeId)) null else NamedPlaceService.Refusal.NOT_FOUND.name
    }
  }

  private fun placesOf(masterId: Long): ZoneAnswer.BestiaPlaces {
    val chests = world.read {
      structures.all().filter { it.kind == StaticEntityKind.CHEST && it.ownerMasterId == masterId && !it.isUnderConstruction }
        .map { ZoneAnswer.BestiaPlaces.ChestSpot(it.id, plane.eastMetres(it.position), plane.northMetres(it.position)) }
    }
    val named = places.of(masterId).map {
      ZoneAnswer.BestiaPlaces.BestiaPlace(
        it.id, it.name, it.kind.name, plane.eastMetres(it.center), plane.northMetres(it.center), it.radius, it.chestStructureId,
      )
    }

    return ZoneAnswer.BestiaPlaces(named, chests)
  }

  /** Runs [change] for an owned master; it answers a refusal, or null when it went through. */
  private fun write(correlationId: String, accountId: Long, masterId: Long, change: () -> String?): BusReply {
    var reply = notFound(correlationId)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        when {
          owned.find(accountId, masterId) == null -> notFound(correlationId)
          else -> when (val refusal = change()) {
            null -> BusReply.ok(correlationId, placesOf(masterId))
            NamedPlaceService.Refusal.NOT_FOUND.name -> notFound(correlationId)
            else -> BusReply.of(correlationId, Status.INVALID, refusal)
          }
        }
      } catch (e: Exception) {
        LOG.error(e) { "A place order of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private fun notFound(correlationId: String) = BusReply.of(correlationId, Status.NOT_FOUND)

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
