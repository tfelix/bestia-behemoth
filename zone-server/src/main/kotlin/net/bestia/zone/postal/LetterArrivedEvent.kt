package net.bestia.zone.postal

/** A pigeon brought [letter] to [recipientMasterId]. Published inside the sending transaction; listen after the commit. */
data class LetterArrivedEvent(
  val recipientAccountId: Long,
  val recipientMasterId: Long,
  val letter: LetterView,
)
