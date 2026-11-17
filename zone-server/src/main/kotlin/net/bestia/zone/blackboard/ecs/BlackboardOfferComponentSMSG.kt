package net.bestia.zone.blackboard.ecs

import net.bestia.bnet.proto.BlackboardOfferComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class BlackboardOfferComponentSMSG(
  override val entityId: Long,
  val levelsLow: Long,
  val levelsHigh: Long,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val component = BlackboardOfferComponentSMSGProto.BlackboardOfferComponentSMSG.newBuilder()
      .setLevelsLow(levelsLow)
      .setLevelsHigh(levelsHigh)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setBlackboardOffer(component))
  }
}
