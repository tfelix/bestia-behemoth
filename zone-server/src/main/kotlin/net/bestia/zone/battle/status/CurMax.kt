package net.bestia.zone.battle.status

import net.bestia.zone.ecs.Dirtyable
import kotlin.math.max
import kotlin.math.min

abstract class CurMax(
  current: Int,
  max: Int
) : Dirtyable {

  private var dirty = true

  open var current: Int = 0
    set(value) {
      val clamped = max(0, min(value, max))
      if (clamped != field) {
        field = clamped
        dirty = true
      }
    }

  open var max: Int = 0
    set(value) {
      require(value >= 0)
      if (value != field) {
        field = value
        dirty = true
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

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }
}