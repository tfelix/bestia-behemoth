package net.bestia.zone.prop.kit

import net.bestia.zone.entity.StaticEntityKind

/** An item that becomes a structure of [kind] after [buildSeconds] of work. What a repair of that kind costs. */
interface BuildingKit {
  val kind: StaticEntityKind
  val kitItemId: Long
  val buildSeconds: Float
}
