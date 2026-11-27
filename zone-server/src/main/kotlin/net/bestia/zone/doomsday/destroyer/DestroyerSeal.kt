package net.bestia.zone.doomsday.destroyer

import net.bestia.zone.casting.ritual.RitualObserver
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * A ritual completed at the lair of a fallen Destroyer renews its seal, so it stays down longer. Heard on the tick,
 * so it knows the lair from what [DestroyerThreat] last told it rather than from the database.
 */
@Component
class DestroyerSeal : RitualObserver {

  /** The lair while the Destroyer lies fallen, null while it walks. */
  @Volatile
  private var lair: Vec3L? = null

  @Volatile
  private var renewed = false

  fun watch(lair: Vec3L?) {
    this.lair = lair
  }

  override fun onRitualCompleted(world: World, at: Vec3L, participants: List<EntityId>) {
    val lair = lair ?: return
    if (hypot((lair.x - at.x).toDouble(), (lair.y - at.y).toDouble()) <= REACH_VOXELS) {
      renewed = true
    }
  }

  /** Whether a ritual renewed the seal since the last ask. */
  fun take(): Boolean {
    val wasRenewed = renewed
    renewed = false
    return wasRenewed
  }

  private companion object {
    /** As close as a ritual must be to reinforce an old seal. */
    const val REACH_VOXELS = 60.0
  }
}
