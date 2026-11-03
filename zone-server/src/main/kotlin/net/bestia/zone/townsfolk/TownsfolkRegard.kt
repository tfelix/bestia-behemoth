package net.bestia.zone.townsfolk

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What a townsperson thinks of a master. The honor slice, which sits above, implements it. */
interface TownsfolkRegard {

  /** What the two questions read, for a system that asks them from inside the tick. */
  val reads: ComponentClassSet

  /** Whether [townsperson] of [settlement] thinks too badly of [master] to talk to them. */
  fun isUnfriendly(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean

  /** Whether [townsperson] of [settlement] is afraid of [master] and keeps away. */
  fun isFeared(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean

  companion object {
    /** Everybody is welcome. Keeps tests of the consumers short. */
    val NONE = object : TownsfolkRegard {
      override val reads: ComponentClassSet = emptySet()

      override fun isUnfriendly(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean {
        return false
      }

      override fun isFeared(world: World, master: EntityId, settlement: Int, townsperson: Long): Boolean {
        return false
      }
    }
  }
}
