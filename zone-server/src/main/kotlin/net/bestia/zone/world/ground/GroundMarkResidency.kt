package net.bestia.zone.world.ground

import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Service

/**
 * Reads a column's graded marks in when somebody comes to see it, and writes them out when nobody is left.
 *
 * ### Why this is not `loadAll` at boot
 *
 * A world holds a handful of scars and can hold a worn or bloodied row for every column anyone has ever
 * crossed, so `ScorchBootRunner`'s approach does not scale here. Residency is pushed in from the subscription
 * instead - the same mechanism `DerivedStore.track` is driven by, and for the same reason.
 *
 * ### One residency for every graded layer
 *
 * The stores are collected rather than named, so a new layer is a bean and not an edit here. They are all held
 * and released together because they are held and released for the same reason: somebody can see that ground.
 *
 * ### A column is tracked per *column*, not per slab
 *
 * `onFirstSubscriber` and `onLastSubscriber` are per chunk, and a column is 256 m of them. A mark belongs to
 * the surface, so tracking is keyed on `(x, y)` and release waits until the whole column is unheld - otherwise
 * a player walking up a hill would evict the ground under their own feet.
 *
 * ### Safe by construction
 *
 * Both callbacks run on the tick thread inside `ChunkStreamSystem`, which is what lets a store keep a plain
 * `HashMap`. This deliberately does not read the subscription service from anywhere else; see
 * `GroundOverlayService`'s KDoc for what that cost when it was tried.
 */
@Service
class GroundMarkResidency(
  private val subscriptions: ChunkSubscriptionService,
  private val stores: List<GroundLevelStore>,
) {

  init {
    subscriptions.onFirstSubscriber { chunk ->
      val column = ColumnKey.of(chunk.x, chunk.y)
      stores.forEach { it.track(column) }
    }

    subscriptions.onLastSubscriber { chunk ->
      if (subscriptions.subscribersOfColumn(chunk.x, chunk.y).isEmpty()) {
        val column = ColumnKey.of(chunk.x, chunk.y)
        stores.forEach { it.release(column) }
      }
    }
  }
}
