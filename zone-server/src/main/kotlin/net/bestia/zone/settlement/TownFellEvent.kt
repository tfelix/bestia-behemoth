package net.bestia.zone.settlement

import net.bestia.zone.BestiaEvent
import net.bestia.zone.geometry.Vec3L

/** A town's sign was destroyed. What only works in a town stops working; see the player settlements page. */
class TownFellEvent(
  source: Any,
  val townId: Long,
  val name: String,
  val sign: Vec3L,
) : BestiaEvent(source)
