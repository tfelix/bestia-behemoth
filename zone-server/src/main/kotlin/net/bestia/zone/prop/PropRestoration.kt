package net.bestia.zone.prop

import net.bestia.zone.prop.persistence.DivergenceState
import org.springframework.stereotype.Component

/**
 * Brings back a generated prop that was destroyed for good, such as a building somebody rebuilt. Tick thread
 * only, like every writer of [WorldObjectDivergenceRegistry].
 */
@Component
class PropRestoration(private val divergence: WorldObjectDivergenceRegistry) {

  /** Whether [propId] is gone and will not come back by itself. */
  fun isDestroyed(propId: Long): Boolean {
    val entry = divergence.of(propId) ?: return false

    return entry.state == DivergenceState.DEPLETED && entry.resumeAt == null
  }

  /**
   * Forgets that the prop was destroyed, so the world generates it again. It stands the next time its column is
   * loaded; a client that holds the column now sees it when it comes back.
   *
   * @return false when the prop was not destroyed
   */
  fun restore(propId: Long): Boolean {
    if (!isDestroyed(propId)) {
      return false
    }
    divergence.evictRegrown(propId)

    return true
  }
}
