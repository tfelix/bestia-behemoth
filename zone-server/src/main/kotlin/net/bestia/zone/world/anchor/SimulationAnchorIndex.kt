package net.bestia.zone.world.anchor

import net.bestia.zone.aoi.AreaOfInterestService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Where the anchors that hold their surroundings stand: what spawners wake around and what keeps AI at full
 * rate. Keyed by entity, unlike `ActivePlayerAOIService`, which is keyed by account.
 */
@Service
class SimulationAnchorIndex {

  private val grid = AreaOfInterestService()
  private val positions = HashMap<EntityId, Vec3L>()

  val size: Int
    get() {
      return positions.size
    }

  fun positions(): Collection<Vec3L> {
    return positions.values
  }

  fun anyWithinHorizontal(x: Long, y: Long, radius: Long): Boolean {
    return grid.anyWithinHorizontal(x, y, radius)
  }

  fun place(id: EntityId, position: Vec3L) {
    if (positions.put(id, position) != position) grid.setEntityPosition(id, position)
  }

  fun remove(id: EntityId) {
    if (positions.remove(id) != null) grid.removeEntityPosition(id)
  }
}
