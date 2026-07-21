package net.bestia.zone.account.master.skill

import net.bestia.bnet.proto.InvestSkillPointCmsgProto
import net.bestia.zone.message.CMSG

data class InvestSkillPointCMSG(
  override val playerId: Long,
  val investedPoints: List<InvestedSkillPoint>
) : CMSG {

  data class InvestedSkillPoint(
    val attackId: Long,
    val amount: Int
  )

  companion object {
    fun fromBnet(
      accountId: Long,
      investSkillPoint: InvestSkillPointCmsgProto.InvestSkillPointCMSG
    ): InvestSkillPointCMSG {
      return InvestSkillPointCMSG(
        playerId = accountId,
        investedPoints = investSkillPoint.investedPointsList.map {
          InvestedSkillPoint(attackId = it.attackId, amount = it.amount)
        }
      )
    }
  }
}
