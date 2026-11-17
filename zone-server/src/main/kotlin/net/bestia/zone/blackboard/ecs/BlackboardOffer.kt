package net.bestia.zone.blackboard.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * The suggested levels of the quests open on the boards, as two 64-bit masks over levels 1 to 128. A min/max range
 * would mark a board for a level-40 master when only level-5 and level-80 quests are open; a mask does not. The
 * same on every board, since every board shows the same list.
 */
class BlackboardOffer : DirtyableComponent() {

  var levelsLow: Long = 0
    private set
  var levelsHigh: Long = 0
    private set

  fun offer(levels: Collection<Int>) {
    var low = 0L
    var high = 0L
    for (level in levels.map { it.coerceIn(1, MAX_LEVEL) }) {
      if (level <= 64) low = low or (1L shl (level - 1)) else high = high or (1L shl (level - 65))
    }
    if (low == levelsLow && high == levelsHigh) return

    levelsLow = low
    levelsHigh = high
    markDirty()
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return BlackboardOfferComponentSMSG(entityId, levelsLow, levelsHigh)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }

  private companion object {
    const val MAX_LEVEL = 128
  }
}
