package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MasterProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.geometry.Vec3L
import java.awt.Color
import net.bestia.bnet.proto.BestiaInfoProto

data class AvailableMasterSMSG(
  val master: List<MasterInfo>,
  val maxAvailableMasterSlots: Int,
  val maxAvailableBestiaSlots: Int
) : SMSG {

  data class MasterInfo(
    val id: Long,
    val name: String,
    val hairColor: Color,
    val skinColor: Color,
    val hair: Hairstyle,
    val face: Face,
    val body: BodyType,
    val position: Vec3L,
    val level: Int,
    val bestias: List<SelfSMSG.BestiaInfo>
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val masterBuilder = MasterProto.Master.newBuilder()
      .setMaxAvailableMasterSlots(maxAvailableMasterSlots)
      .setMaxAvailableBestiaSlots(maxAvailableBestiaSlots)

    master.forEach { masterInfo ->
      val position = Vec3OuterClass.Vec3.newBuilder()
        .setX(masterInfo.position.x)
        .setY(masterInfo.position.y)
        .setZ(masterInfo.position.z)
        .build()

      val skinColor = MasterProto.Color.newBuilder()
        .setR(masterInfo.skinColor.red)
        .setG(masterInfo.skinColor.green)
        .setB(masterInfo.skinColor.blue)
        .build()

      val hairColor = MasterProto.Color.newBuilder()
        .setR(masterInfo.hairColor.red)
        .setG(masterInfo.hairColor.green)
        .setB(masterInfo.hairColor.blue)
        .build()

      val protoMasterInfoBuilder = MasterProto.MasterInfo.newBuilder()
        .setMasterId(masterInfo.id)
        .setName(masterInfo.name)
        .setLevel(masterInfo.level)
        .setPosition(position)
        .setBody(mapBodyType(masterInfo.body))
        .setSkinColor(skinColor)
        .setHairColor(hairColor)
        .setFace(mapFace(masterInfo.face))

      // Add bestias for this master
      masterInfo.bestias.forEach { bestia ->
        val bestiaPosition = Vec3OuterClass.Vec3.newBuilder()
          .setX(bestia.position.x)
          .setY(bestia.position.y)
          .setZ(bestia.position.z)
          .build()

        val protoBestiaInfo = BestiaInfoProto.BestiaInfo.newBuilder()
          .setEntityId(bestia.entityId)
          .setMobId(bestia.mobId)
          .setName(bestia.name ?: "")
          .setLevel(bestia.level)
          .setPosition(bestiaPosition)
          .build()

        protoMasterInfoBuilder.addBestias(protoBestiaInfo)
      }

      masterBuilder.addMaster(protoMasterInfoBuilder.build())
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setMaster(masterBuilder.build())
      .build()
  }

  private fun mapBodyType(bodyType: BodyType): MasterProto.BodyType {
    return when (bodyType) {
      BodyType.BODY_M_1 -> MasterProto.BodyType.BODY_M_1
    }
  }

  private fun mapFace(face: Face): MasterProto.Face {
    return when (face) {
      Face.FACE_1 -> MasterProto.Face.FACE_1
    }
  }
}