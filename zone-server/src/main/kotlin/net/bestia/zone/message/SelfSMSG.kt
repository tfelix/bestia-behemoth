package net.bestia.zone.message

import net.bestia.bnet.proto.BestiaInfoProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SelfSMSGProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L

data class SelfSMSG(
  val masterId: Long,
  val masterEntityId: Long,
  val availableBestias: List<BestiaInfo>
) : SMSG {

  data class BestiaInfo(
    val entityId: Long,
    val mobId: Int,
    /**
     * Name is optional. If not given fall back to the
     * mob name from the client.
     */
    val name: String?,
    val level: Int,
    val position: Vec3L
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val selfBuilder = SelfSMSGProto.SelfSMSG.newBuilder()
      .setMasterId(masterId)
      .setMasterEntityId(masterEntityId)

    availableBestias.forEach { bestia ->
      val position = Vec3OuterClass.Vec3.newBuilder()
        .setX(bestia.position.x)
        .setY(bestia.position.y)
        .setZ(bestia.position.z)
        .build()

      val protoBestiaInfo = BestiaInfoProto.BestiaInfo.newBuilder()
        .setEntityId(bestia.entityId)
        .setMobId(bestia.mobId)
        .setName(bestia.name)
        .setLevel(bestia.level)
        .setPosition(position)
        .build()

      selfBuilder.addAvailableBestias(protoBestiaInfo)
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setSelf(selfBuilder.build())
      .build()
  }
}
