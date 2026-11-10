package net.bestia.zone.prop.chest

import net.bestia.bnet.proto.ChestOpenedSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class ChestOpenedSMSG(
  val entityId: EntityId,
  val slots: Int,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val opened = ChestOpenedSMSGProto.ChestOpenedSMSG.newBuilder()
      .setEntityId(entityId)
      .setSlots(slots)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setChestOpened(opened)
      .build()
  }
}
