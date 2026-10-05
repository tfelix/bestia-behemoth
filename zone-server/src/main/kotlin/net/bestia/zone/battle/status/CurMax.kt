package net.bestia.zone.battle.status

import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.Dirtyable
import kotlin.math.max
import kotlin.math.min

abstract class CurMax(
  current: Int,
  max: Int
) : Dirtyable {
  override val dirtyFlag = DirtyFlag()

  open var current: Int = 0
    set(value) {
      val clamped = max(0, min(value, max))
      if (clamped != field) {
        field = clamped
        markDirty()
      }
    }

  open var max: Int = 0
    set(value) {
      require(value >= 0)
      if (value != field) {
        field = value
        markDirty()
      }

      if (current > value) {
        current = value
      }
    }

  init {
    this.max = max
    this.current = current
  }

  override fun toString(): String {
    return "$current/$max"
  }
}