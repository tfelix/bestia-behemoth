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
    )
  }

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

    data class Species(val id: Long, val identifier: String, val kind: String, val element: String, val level: Int)
  }

  data class MapInfo(
    val worldMapVersion: String,
    val tileSize: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val worldWidthMetres: Double,
    val worldHeightMetres: Double,
  ) : ZoneAnswer

  /** The PNG itself travels as the reply's body, not in the JSON. */
  data class Tile(val etag: String, val shared: Boolean) : ZoneAnswer

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

  /** The config as stored, which may differ from the one asked for: aggression is clamped. */
  data class Stance(val playerBestiaId: Long, val stance: String, val aggression: Int) : ZoneAnswer
}
