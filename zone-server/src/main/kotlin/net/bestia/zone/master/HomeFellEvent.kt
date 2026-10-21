package net.bestia.zone.master

import net.bestia.zone.BestiaEvent

/** A town fell and its masters were given [newHome]. Published from a database job, not the tick. */
class HomeFellEvent(
  source: Any,
  val masters: List<Rehomed>,
  val fallen: String,
  val newHome: String,
) : BestiaEvent(source) {

  data class Rehomed(val accountId: Long, val masterId: Long, val name: String)
}
