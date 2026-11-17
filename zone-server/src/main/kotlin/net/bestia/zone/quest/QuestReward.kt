package net.bestia.zone.quest

data class QuestReward(
  val kind: Kind,
  val amount: Long,
) {

  enum class Kind {
    COINS_PER_UNIT,
  }
}
