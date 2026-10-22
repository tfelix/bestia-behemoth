package net.bestia.bus.contract

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * What the api-service may ask a zone on behalf of an account. The account travels on the [BusRequest], never in
 * here, so a query cannot name somebody else's; every master id in a query is checked against it by the zone.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface ZoneQuery {

  data object ListMasters : ZoneQuery

  data class MasterSheet(val masterId: Long) : ZoneQuery

  data class BestiaRoster(val masterId: Long) : ZoneQuery

  data class Inventory(val masterId: Long) : ZoneQuery

  data class PartyRoster(val masterId: Long) : ZoneQuery

  data object Catalog : ZoneQuery

  data object MapMeta : ZoneQuery

  data class MapTile(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

  data class MapPlaces(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

  data class MapServices(val masterId: Long, val level: Int, val x: Long, val y: Long, val ifNoneMatch: String?) : ZoneQuery

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
}
