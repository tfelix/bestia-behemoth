package net.bestia.bus.contract

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A zone's answer to one [ZoneQuery]. Positions are world metres on the map plane (east, north), already
 * converted from voxels, so no reader needs the world's voxel size to place a marker.
 *
 * `asOfEpochMs` is when the figure was true: now for something read from the live world, the last save for
 * something read from the database.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface ZoneAnswer {

  data class Masters(val masters: List<Master>) : ZoneAnswer {
    data class Master(
      val id: Long,
      val slot: Int,
      val name: String,
      val level: Int,
      val home: String,
      val inWorld: Boolean,
    )
  }

  data class Sheet(
    val id: Long,
    val name: String,
    val level: Int,
    val exp: Int,
    val skillPoints: Int,
    val statusPoints: Int,
    val strength: Int,
    val vitality: Int,
    val intelligence: Int,
    val dexterity: Int,
    val willpower: Int,
    val agility: Int,
    val currentHealth: Int?,
    val home: String,
    val eastMetres: Double,
    val northMetres: Double,
    val inWorld: Boolean,
    val asOfEpochMs: Long,
  ) : ZoneAnswer

  data class Roster(val bestias: List<Bestia>) : ZoneAnswer {
    data class Bestia(
      val id: Long,
      val name: String?,
      val speciesId: Long,
      val species: String,
      val level: Int,
      val stance: String,
      val aggression: Int,
      val inWorld: Boolean,
      val eastMetres: Double,
      val northMetres: Double,
      val asOfEpochMs: Long,
      /** How far it walks in a second, buffs included, so the app can tell how long a way takes. */
      val metresPerSecond: Double,
      /** Where it is travelling, null when it stands. */
      val journey: Journey? = null,
    )
  }

  /** [route] is the way still ahead as planned, coarsely; [waypoints] from [nextIndex] on are still to visit. */
  data class Journey(
    val name: String?,
    val waypoints: List<MapPoint>,
    val nextIndex: Int,
    val route: List<MapPoint>,
  ) : ZoneAnswer

  /** The master's routes, oldest first. */
  data class RouteList(val routes: List<Route>) : ZoneAnswer {
    /** [customName] is false while [name] is the one made from where it starts and ends. */
    data class Route(val id: Long, val name: String, val customName: Boolean, val waypoints: List<MapPoint>)
  }

  /** A write that has nothing to say back but that it was done. */
  data object Done : ZoneAnswer

  data class Bag(val holders: List<Holder>) : ZoneAnswer {
    data class Holder(
      val kind: String,
      val id: Long,
      val name: String?,
      val slots: List<Slot>,
    )

    data class Slot(
      val itemId: Long,
      val item: String,
      val amount: Int,
      val equippedIn: String?,
      val durability: Int,
      val maxDurability: Int,
      val upgradeLevel: Int,
    )
  }

  /** [party] is null when the master is in none. */
  data class PartyInfo(val party: Party?) : ZoneAnswer {
    data class Party(val name: String, val ownerMasterId: Long, val members: List<Member>)

    data class Member(val masterId: Long, val name: String, val level: Int, val online: Boolean)
  }

  data class GuildInfo(val guild: Guild?) : ZoneAnswer {
    /** [emblemHash] names the PNG at `/v1/companion/emblems/{hash}.png`; empty while the guild has none. */
    data class Guild(
      val name: String,
      val level: Int,
      val motdTitle: String,
      val motdBody: String,
      val emblemHash: String,
      val members: List<Member>,
    )

    /** [lastAreaName] and [lastSeenEpochMillis] say where and when an offline member left the world. */
    data class Member(
      val masterId: Long,
      val name: String,
      val level: Int,
      val rankTitle: String,
      val isLeader: Boolean,
      val online: Boolean,
      val lastAreaName: String,
      val lastSeenEpochMillis: Long,
    )
  }

  /** Identifiers, not display names: names are translated by the reader, from the same tables as the client. */
  data class CatalogData(val version: String, val items: List<Item>, val bestias: List<Species>) : ZoneAnswer {
    data class Item(
      val id: Long,
      val identifier: String,
      val type: String,
      val weight: Int,
      val equipSlot: String?,
      val stackable: Boolean,
    )

    /** [habitat] holds biome names, empty for any land. [inCompendium] is false for townsfolk and test creatures. */
    data class Species(
      val id: Long,
      val identifier: String,
      val kind: String,
      val element: String,
      val level: Int,
      val habitat: List<String>,
      val inCompendium: Boolean,
    )
  }

  /** Only the species the master has seen; the rest of the catalogue is unseen. */
  data class CompendiumData(val entries: List<Entry>) : ZoneAnswer {
    data class Entry(val speciesId: Long, val caught: Boolean, val firstSeenEpochMs: Long)
  }

  /** [metresPerVoxel] is for a reader that places entities by their voxel position, as the game client does. */
  data class MapInfo(
    val worldMapVersion: String,
    val tileSize: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val worldWidthMetres: Double,
    val worldHeightMetres: Double,
    val metresPerVoxel: Double,
  ) : ZoneAnswer

  /** The PNG itself travels as the reply's body, not in the JSON. */
  data class Tile(val etag: String, val shared: Boolean) : ZoneAnswer

  /** A guild emblem's PNG, in the reply's body. Never changes under its [hash]. */
  data class Emblem(val hash: String) : ZoneAnswer

  data class Places(val etag: String, val shared: Boolean, val places: List<Place>) : ZoneAnswer {
    data class Place(
      val id: Long,
      val kind: String,
      val x: Double,
      val y: Double,
      val name: String?,
      val tier: String?,
      val poi: String?,
    )
  }

  /**
   * What a tile's services are: shops, inns, markets, ward stones, as they stand now. [openFrom] and [openTo] are
   * game hours, null for something with no hours.
   */
  data class Services(val etag: String, val services: List<Service>) : ZoneAnswer {
    data class Service(
      val id: Long,
      val kind: String,
      val business: String?,
      val x: Double,
      val y: Double,
      val settlement: String?,
      val openFrom: Int?,
      val openTo: Int?,
    )
  }

  data class TicketOwner(val accountId: Long) : ZoneAnswer

  /** The config as stored, which may differ from the one asked for: aggression is clamped. */
  data class Stance(val playerBestiaId: Long, val stance: String, val aggression: Int) : ZoneAnswer

  /**
   * Whether a chat line went out. [refusal] is null when it did, else why not: CHAT_LOCKED (Basic Skill below the
   * chat rank), NOT_IN_PARTY or NOT_IN_GUILD.
   */
  data class ChatSent(val refusal: String?) : ZoneAnswer {
    companion object {
      const val CHAT_LOCKED = "CHAT_LOCKED"
      const val NOT_IN_PARTY = "NOT_IN_PARTY"
      const val NOT_IN_GUILD = "NOT_IN_GUILD"
    }
  }
}
