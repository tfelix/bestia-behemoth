package net.bestia.zone.crafting

import net.bestia.zone.util.PlayerBestiaId

/**
 * The Bestias locked into a Carving Table, and the ones the viewer could lock in, for its window. They live in the
 * database, so this is read off the tick. Implemented by the etching slice, a slice above this one.
 */
interface CarvingTableContents {

  fun load(structureId: Long, viewerMasterId: Long): Contents

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
