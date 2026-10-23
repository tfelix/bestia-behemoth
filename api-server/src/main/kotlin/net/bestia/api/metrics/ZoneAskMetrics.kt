package net.bestia.api.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneQuery
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.EnumMap
import java.util.concurrent.TimeUnit

/** [ZoneQuery]'s subtypes as a closed tag value: the `query` of `api.zone.ask`. Never anything that varies per request. */
enum class ZoneQueryKind {
  LIST_MASTERS,
  MASTER_SHEET,
  BESTIA_ROSTER,
  INVENTORY,
  PARTY_ROSTER,
  COMPENDIUM,
  GUILD_ROSTER,
  CATALOG,
  MAP_META,
  MAP_TILE,
  MAP_PLACES,
  MAP_SERVICES,
  HABITAT_TILE,
  WHOSE_TICKET,
  GUILD_EMBLEM,
  SET_STANCE,
  SEND_CHAT,
  SEND_ON_JOURNEY,
  STOP_JOURNEY,
  SAVED_ROUTES,
  SAVE_ROUTE,
  RENAME_ROUTE,
  DELETE_ROUTE,
  ;

  companion object {
    fun of(query: ZoneQuery): ZoneQueryKind {
      return when (query) {
        is ZoneQuery.ListMasters -> LIST_MASTERS
        is ZoneQuery.MasterSheet -> MASTER_SHEET
        is ZoneQuery.BestiaRoster -> BESTIA_ROSTER
        is ZoneQuery.Inventory -> INVENTORY
        is ZoneQuery.PartyRoster -> PARTY_ROSTER
        is ZoneQuery.Compendium -> COMPENDIUM
        is ZoneQuery.GuildRoster -> GUILD_ROSTER
        is ZoneQuery.Catalog -> CATALOG
        is ZoneQuery.MapMeta -> MAP_META
        is ZoneQuery.MapTile -> MAP_TILE
        is ZoneQuery.MapPlaces -> MAP_PLACES
        is ZoneQuery.MapServices -> MAP_SERVICES
        is ZoneQuery.HabitatTile -> HABITAT_TILE
        is ZoneQuery.WhoseTicket -> WHOSE_TICKET
        is ZoneQuery.GuildEmblem -> GUILD_EMBLEM
        is ZoneQuery.SetStance -> SET_STANCE
        is ZoneQuery.SendChat -> SEND_CHAT
        is ZoneQuery.SendOnJourney -> SEND_ON_JOURNEY
        is ZoneQuery.StopJourney -> STOP_JOURNEY
        is ZoneQuery.SavedRoutes -> SAVED_ROUTES
        is ZoneQuery.SaveRoute -> SAVE_ROUTE
        is ZoneQuery.RenameRoute -> RENAME_ROUTE
        is ZoneQuery.DeleteRoute -> DELETE_ROUTE
      }
    }
  }
}

/** How an ask ended: a reply of some status, or none at all. */
enum class AskResult {
  OK,
  NOT_MODIFIED,
  NOT_FOUND,
  INVALID,
  BUSY,
  TIMEOUT,
  UNAVAILABLE,
  ERROR,
  ;

  companion object {
    fun of(status: BusReply.Status): AskResult {
      return when (status) {
        BusReply.Status.OK -> OK
        BusReply.Status.NOT_MODIFIED -> NOT_MODIFIED
        BusReply.Status.NOT_FOUND -> NOT_FOUND
        BusReply.Status.INVALID -> INVALID
        BusReply.Status.BUSY -> BUSY
      }
    }
  }
}

/**
 * `api.zone.ask`, how long the zone took to answer and how each ask ended, by query type. Every combination is
 * registered at start, so a series that has not happened yet reads zero rather than missing.
 */
@Component
class ZoneAskMetrics(registry: MeterRegistry) {

  private val timers = EnumMap<ZoneQueryKind, EnumMap<AskResult, Timer>>(ZoneQueryKind::class.java)

  init {
    for (kind in ZoneQueryKind.entries) {
      val byResult = EnumMap<AskResult, Timer>(AskResult::class.java)
      for (result in AskResult.entries) {
        byResult[result] = Timer.builder("api.zone.ask")
          .description("Time from sending a request to the zone until its reply, or the failure that ended the wait")
          .tag("query", kind.name.lowercase())
          .tag("result", result.name.lowercase())
          .serviceLevelObjectives(*SLOS)
          .register(registry)
      }
      timers[kind] = byResult
    }
  }

  fun record(query: ZoneQuery, result: AskResult, startedNanos: Long) {
    timers.getValue(ZoneQueryKind.of(query)).getValue(result).record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS)
  }

  private companion object {
    val SLOS = arrayOf(10, 50, 100, 250, 500, 1_000, 2_500, 5_000).map { Duration.ofMillis(it.toLong()) }.toTypedArray()
  }
}
