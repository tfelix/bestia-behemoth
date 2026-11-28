package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.BestiaActivitySmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class BestiaActivitySMSG(
  val entityId: EntityId,
  val playerBestiaId: Long,
  val activity: BestiaActivity,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val changed = BestiaActivitySmsgProto.BestiaActivitySMSG.newBuilder()
      .setEntityId(entityId)
      .setPlayerBestiaId(playerBestiaId)
      .setActivity(activity.toBnet())

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaActivity(changed)
      .build()
  }
}
