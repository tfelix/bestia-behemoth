package net.bestia.zone.account.master.status

import net.bestia.bnet.proto.InvestStatusPointCmsgProto
import net.bestia.zone.message.CMSG

data class InvestStatusPointCMSG(
  override val playerId: Long,
  val investedPoints: List<InvestedStatusPoint>
) : CMSG {

  data class InvestedStatusPoint(
    val attribute: StatusAttribute,
    val amount: Int
  )

  companion object {
    fun fromBnet(
      accountId: Long,
      investStatusPoint: InvestStatusPointCmsgProto.InvestStatusPointCMSG
    ): InvestStatusPointCMSG {
      return InvestStatusPointCMSG(
        playerId = accountId,
        investedPoints = investStatusPoint.investedPointsList.map {
          InvestedStatusPoint(attribute = it.attribute.toDomain(), amount = it.amount)
        }
      )
    }

    private fun InvestStatusPointCmsgProto.StatusAttribute.toDomain(): StatusAttribute = when (this) {
      InvestStatusPointCmsgProto.StatusAttribute.STRENGTH -> StatusAttribute.STRENGTH
      InvestStatusPointCmsgProto.StatusAttribute.AGILITY -> StatusAttribute.AGILITY
      InvestStatusPointCmsgProto.StatusAttribute.VITALITY -> StatusAttribute.VITALITY
      InvestStatusPointCmsgProto.StatusAttribute.INTELLIGENCE -> StatusAttribute.INTELLIGENCE
      InvestStatusPointCmsgProto.StatusAttribute.DEXTERITY -> StatusAttribute.DEXTERITY
      InvestStatusPointCmsgProto.StatusAttribute.WILLPOWER -> StatusAttribute.WILLPOWER
      else -> throw IllegalArgumentException("Unknown StatusAttribute: $this")
    }
  }
}
