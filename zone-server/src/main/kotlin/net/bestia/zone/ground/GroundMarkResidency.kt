package net.bestia.zone.ground

import net.bestia.zone.world.residency.ChunkResidency
import org.springframework.stereotype.Service

/**
 * Reads a column's graded marks in when the column becomes resident, and writes them out when it stops being.
 *
 * ### Why this is not `loadAll` at boot
 *
 * A world holds a handful of scars and can hold a worn or bloodied row for every column anyone has ever
 * crossed, so `ScorchBootRunner`'s approach does not scale here. Residency is pushed in from [ChunkResidency]
 * instead - the same mechanism `DerivedStore.track` is driven by, and for the same reason.
 *
 * ### One residency for every graded layer
 *
 * The stores are collected rather than named, so a new layer is a bean and not an edit here. They are all held
 * and released together because they are held and released for the same reason: somebody is near that ground.
 *
 * ### A column is tracked per *column*, not per slab
 *
 * [ChunkResidency]'s hooks are per chunk, and a column is 256 m of them. A mark belongs to
 * the surface, so tracking is keyed on `(x, y)` and release waits until the whole column is unheld - otherwise
 * a player walking up a hill would evict the ground under their own feet.
 *
 * ### Safe by construction
 *
 * Both callbacks run on the tick thread, which is what lets a store keep a plain `HashMap`. This deliberately
 * does not read residency from anywhere else; see `GroundOverlayService`'s KDoc for what that cost when it
 * was tried.
 */
@Service
class GroundMarkResidency(
  private val residency: ChunkResidency,
  private val stores: List<GroundLevelStore>,
) {

  init {
    residency.onFirstHolder { chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      stores.forEach { it.track(column) }
    }

    residency.onLastHolder { chunk ->
      if (!residency.isColumnResident(chunk.x, chunk.y)) {
        val column = ColumnKey.of(chunk.x, chunk.y)
        stores.forEach { it.release(column) }
      }
    }
  }
}
