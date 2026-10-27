package net.bestia.bus.contract

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * What the api-server may ask a zone on behalf of an account. The account travels on the [BusRequest], never in
 * here, so a query cannot name somebody else's; every master id in a query is checked against it by the zone.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface ZoneQuery {

  data object ListMasters : ZoneQuery

  data class MasterSheet(val masterId: Long) : ZoneQuery

  data class BestiaRoster(val masterId: Long) : ZoneQuery

  data class Inventory(val masterId: Long) : ZoneQuery

  data class PartyRoster(val masterId: Long) : ZoneQuery

  /** The species [masterId] has seen, from its Bestia Compendium. */
  data class Compendium(val masterId: Long) : ZoneQuery

  data class GuildRoster(val masterId: Long) : ZoneQuery

  data object Catalog : ZoneQuery

  data object MapMeta : ZoneQuery

  data class MapTile(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

  data class MapPlaces(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

  data class MapServices(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

  /** One named place and its services, if the master charted it. Answered with [ZoneAnswer.PlaceDetail]. */
  data class MapPlace(val masterId: Long, val placeId: Long) : ZoneQuery

  /** The places the master has charted whose names contain [text]. Answered with [ZoneAnswer.Found]. */
  data class MapFind(val masterId: Long, val text: String) : ZoneQuery

  /** Where a species the master has seen lives, as an overlay for a map tile. Answered like [MapTile]. */
  data class HabitatTile(
    val masterId: Long,
    val speciesId: Long,
    val level: Int,
    val x: Long,
    val y: Long,
    val ifNoneMatch: String?,
  ) : ZoneQuery

  /**
   * Whose game connection holds [ticket], the HTTP ticket the zone hands its client at the socket handshake. The
   * one query sent before the asker is known, so its request carries account 0 and the zone ignores it.
   */
  data class WhoseTicket(val ticket: String) : ZoneQuery

  /**
   * A guild emblem by the hash that names it. Not checked against the asker: an emblem shows on every member's
   * name tag, so whoever can see one may see the picture.
   */
  data class GuildEmblem(val hash: String) : ZoneQuery

  /** A standing order for an owned bestia. A write: send it with an idempotency key. */
  data class SetStance(
    val masterId: Long,
    val playerBestiaId: Long,
    val stance: String,
    val aggression: Int,
  ) : ZoneQuery

  /** A line of party or guild chat ([channel] PARTY or GUILD), said as [masterId]. A write: send it with an idempotency key. */
  data class SendChat(val masterId: Long, val channel: String, val text: String) : ZoneQuery

  /**
   * Sends an owned bestia through [waypoints], or along the saved route under [routeKey] in the master's notebook -
   * from its last point back when [reverse]. A write: send it with an idempotency key.
   */
  data class SendOnJourney(
    val masterId: Long,
    val playerBestiaId: Long,
    val waypoints: List<MapPoint>,
    val routeKey: String? = null,
    val reverse: Boolean = false,
  ) : ZoneQuery

  /** Stops an owned bestia where it stands. A write: send it with an idempotency key. */
  data class StopJourney(val masterId: Long, val playerBestiaId: Long) : ZoneQuery

  /**
   * One collection of the player's notebook - routes, marks, map preferences - of [masterId], or of the whole
   * account when it is null. Answered with [ZoneAnswer.Notebook].
   */
  data class NotebookList(val masterId: Long?, val collection: String) : ZoneQuery

  /**
   * Keeps [json] under [key], or under a new key when it is null. With [baseRevision] it lands only while the entry
   * is still at it, 0 for one that must not exist yet. Answered with [ZoneAnswer.NotebookWritten]. A write.
   */
  data class NotebookPut(
    val masterId: Long?,
    val collection: String,
    val key: String?,
    val json: String,
    val baseRevision: Long?,
  ) : ZoneQuery

  /** Answered with [ZoneAnswer.NotebookWritten]. A write. */
  data class NotebookDelete(val masterId: Long?, val collection: String, val key: String, val baseRevision: Long?) : ZoneQuery
}
