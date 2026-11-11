package net.bestia.zone.spellscroll.persistence

/** One [SpellScrollInscription] as plain values, keyed by the scroll's unique id. */
data class InscribedScroll(
  val uniqueId: Long,
  val skillId: Long,
  val level: Int,
)
