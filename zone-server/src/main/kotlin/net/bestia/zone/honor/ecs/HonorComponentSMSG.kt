package net.bestia.zone.honor.ecs

import net.bestia.bnet.proto.HonorComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class HonorComponentSMSG(
  override val entityId: Long,
  val globalHonor: Int,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val honor = HonorComponentSMSGProto.HonorComponentSMSG.newBuilder().setGlobalHonor(globalHonor)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setHonor(honor))
  }
}
