package net.bestia.zone.ai.domain.townsfolk

/**
 * How many ways a trade has of saying the two things every one of them gets asked.
 *
 * Counts and not the words, exactly as `HistoryLine` is: the phrasings live in the client's translation
 * file and the server's whole interest in a sentence it will never read is how many there are to pick
 * between. What that buys is that two guards in one town do not greet a player identically.
 */
data class OccupationDialog(
  /** Phrasings of the opening line, keyed `TALK_GREETING_<OCCUPATION>_<n>`. */
  val greetings: Int = 1,

  /** Phrasings of "what is it you do here", keyed `TALK_TRADE_<OCCUPATION>_<n>`. */
  val trade: Int = 1,
) {

  init {
    require(greetings >= 1) { "an occupation with no greeting cannot be spoken to, was $greetings" }
    require(trade >= 1) { "an occupation with nothing to say about its trade, was $trade" }
  }

  companion object {
    /** One of each. A trade that has not been given its own voice yet still has one. */
    val ORDINARY = OccupationDialog()
  }
}
