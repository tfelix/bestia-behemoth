package net.bestia.zone.world.ground

import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.springframework.stereotype.Service

/**
 * Reads a column's wear in when somebody comes to see it, and writes it out when nobody is left.
 *
 * ### Why this is not `loadAll` at boot
 *
 * A world holds a handful of scars and can hold a worn row for every column anyone has ever crossed, so
 * `ScorchBootRunner`'s approach does not scale here. Residency is pushed in from the subscription instead -
 * the same mechanism `DerivedStore.track` is driven by, and for the same reason.
 *
 * ### A column is tracked per *column*, not per slab
 *
 * `onFirstSubscriber` and `onLastSubscriber` are per chunk, and a column is 256 m of them. Wear belongs to the
 * surface, so tracking is keyed on `(x, y)` and release waits until the whole column is unheld - otherwise a
 * player walking up a hill would evict the ground under their own feet.
 *
 * ### Safe by construction
 *
 * Both callbacks run on the tick thread inside `ChunkStreamSystem`, which is what lets the registry keep a
 * plain `HashMap`. This deliberately does not read the subscription service from anywhere else; see
 * `GroundOverlayService`'s KDoc for what that cost when it was tried.
 */
@Service
class GroundWearResidency(
  private val subscriptions: ChunkSubscriptionService,
  private val registry: GroundWearRegistry,
) {

  init {
    subscriptions.onFirstSubscriber { chunk ->
      registry.track(ColumnKey.of(chunk.x, chunk.y))
    }

    subscriptions.onLastSubscriber { chunk ->
      if (subscriptions.subscribersOfColumn(chunk.x, chunk.y).isEmpty()) {
        registry.release(ColumnKey.of(chunk.x, chunk.y))
      }
    }
  }
}
