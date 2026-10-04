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
  ) {

    fun toBnet(): BestiaInfoProto.BestiaInfo {
      val position = Vec3OuterClass.Vec3.newBuilder()
        .setX(position.x)
        .setY(position.y)
        .setZ(position.z)
        .build()

      // Proto3 strings have no null: the empty string is the "no name" the client falls back from.
      return BestiaInfoProto.BestiaInfo.newBuilder()
        .setEntityId(entityId)
        .setMobId(mobId)
        .setName(name ?: "")
        .setLevel(level)
        .setPosition(position)
        .build()
    }
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val selfBuilder = SelfSMSGProto.SelfSMSG.newBuilder()
      .setMasterId(masterId)
      .setMasterEntityId(masterEntityId)

    availableBestias.forEach { bestia ->
      selfBuilder.addAvailableBestias(bestia.toBnet())
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setSelf(selfBuilder.build())
      .build()
  }
}
