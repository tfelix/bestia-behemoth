package net.bestia.zone.identity.ecs

import net.bestia.bnet.proto.BestiaTagComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class BestiaTagComponentSMSG(
  override val entityId: Long,
  val name: String?,
  val ownerName: String,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val tag = BestiaTagComponentSMSGProto.BestiaTagComponentSMSG.newBuilder()
      .setName(name.orEmpty())
      .setOwnerName(ownerName)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setBestiaTag(tag))
  }
}
