package net.bestia.zone.quest

enum class QuestStatus {
  OPEN,

  /** The poster withdrew it: work handed in before the notice runs out still counts. */
  CLOSING,
  COMPLETED,
  EXPIRED,
  WITHDRAWN,

  /** Somebody else took the one prize the quest was about. */
  SUPERSEDED;

  val takesWork: Boolean
    get() = this == OPEN || this == CLOSING

  val isEnded: Boolean
    get() = !takesWork
}
