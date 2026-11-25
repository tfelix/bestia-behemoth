package net.bestia.zone.crafting

import net.bestia.zone.util.PlayerBestiaId

/**
 * The Bestias locked into a Carving Table, and the ones the viewer could lock in, for its window. They live in the
 * database, so this is read off the tick. Implemented by the etching slice, a slice above this one.
 */
interface CarvingTableContents {

  /** [etchSecondsPerLevelSquared] is the viewer's etching time, see [CraftBonusService.craftSeconds]. */
  fun load(structureId: Long, viewerMasterId: Long, etchSecondsPerLevelSquared: Float): Contents

  data class Contents(val deposited: List<Deposited>, val depositable: List<Depositable>)

  /** A Bestia locked into the table, any master's. */
  data class Deposited(
    val playerBestiaId: PlayerBestiaId,
    val speciesId: Long,
    val level: Int,
    val name: String?,
    val ownerName: String,
    /** The viewer owns it. */
    val mine: Boolean,
    /** How long the viewer would take to etch it. */
    val etchSeconds: Int = 0,
    /** Seconds left of the etching under way on it, 0 when none is. */
    val etchSecondsLeft: Int = 0,
    /** Who is etching it, while an etching is under way. */
    val etcherName: String? = null,
    /** The viewer is the one etching it. */
    val etchingMine: Boolean = false,
  )

  /** One of the viewer's own Bestias that is not locked in anywhere. */
  data class Depositable(
    val playerBestiaId: PlayerBestiaId,
    val speciesId: Long,
    val level: Int,
    val name: String?,
    val inStorage: Boolean,
  )
}
