package net.bestia.zone.dialog.conversation

/** What picking an option does, so the client can render it without understanding the topic. */
enum class OptionKind {
  /** Says something and offers more. The ordinary case. */
  TALK,

  /** Does something other than talk - opening a shop, taking a contract. */
  ACTION,

  /** Returns to the options one level up. */
  BACK,

  /** Ends the conversation. */
  END,
}
