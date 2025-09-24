package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MasterProto
import net.bestia.bnet.proto.MasterVisualComponentSMSGProto
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import java.awt.Color

data class MasterVisualComponentSMSG(
  val entityId: Long,
  val skinColor: Color,
  val hairColor: Color,
  val face: Face,
  val body: BodyType,
  val hair: Hairstyle
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val skinColorProto = MasterProto.Color.newBuilder()
      .setR(skinColor.red)
      .setG(skinColor.green)
      .setB(skinColor.blue)
      .build()

    val hairColorProto = MasterProto.Color.newBuilder()
      .setR(hairColor.red)
      .setG(hairColor.green)
      .setB(hairColor.blue)
      .build()

    val masterVisualComponent = MasterVisualComponentSMSGProto.MasterVisualComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setSkinColor(skinColorProto)
      .setHairColor(hairColorProto)
      .setFace(mapFace(face))
      .setBody(mapBodyType(body))
      .setHair(mapHairstyle(hair))
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompMasterVisual(masterVisualComponent)
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

  private fun mapHairstyle(hair: Hairstyle): MasterProto.Hairstyle {
    return when (hair) {
      Hairstyle.HAIR_1 -> MasterProto.Hairstyle.HAIR_1
    }
  }
}
