package net.bestia.zone.account.master.skill

/**
 * A request to spend [amount] skill points on the tree node for [skillId].
 */
data class SkillPointInvestment(
  val skillId: Long,
  val amount: Int
) {
  init {
    require(amount > 0) { "amount must be positive" }
  }
}