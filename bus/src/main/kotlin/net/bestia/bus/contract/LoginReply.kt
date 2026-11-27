package net.bestia.bus.contract

data class LoginReply(val correlationId: String, val outcome: Outcome) {

  enum class Outcome {
    DONE,

    /** The note is empty or too long, or the ban duration out of range. */
    INVALID,

    /** The actor is unknown, banned, lacks the authority, or does not outrank the target. */
    NOT_ALLOWED,

    NO_SUCH_ACCOUNT,

    /** The login server failed while acting; the command may or may not have taken effect. */
    FAILED,
  }
}
