package net.bestia.zone.master.ecs

import net.bestia.bnet.proto.MasterProto
import net.bestia.bnet.proto.MasterVisualComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.message.EntitySMSG
import java.awt.Color

data class MasterVisualComponentSMSG(
  override val entityId: Long,
  val name: String,
  val skinColor: Color,
  val hairColor: Color,
  val face: Face,
  val body: BodyType,
  val hair: Hairstyle
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
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
      .setName(name)
      .setSkinColor(skinColorProto)
      .setHairColor(hairColorProto)
      .setFace(mapFace(face))
      .setBody(mapBodyType(body))
      .setHair(mapHairstyle(hair))
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setMasterVisual(masterVisualComponent))
  }

  private fun mapBodyType(bodyType: BodyType): MasterProto.BodyType {
    return when (bodyType) {
      BodyType.BODY_M_1 -> MasterProto.BodyType.BODY_M_1
    }
  }

  private fun mapFace(face: Face): MasterProto.Face {
    return when (face) {
      Face.FACE_1 -> MasterProto.Face.FACE_1
      Face.FACE_2 -> MasterProto.Face.FACE_2
      Face.FACE_3 -> MasterProto.Face.FACE_3
      Face.FACE_4 -> MasterProto.Face.FACE_4
      Face.FACE_5 -> MasterProto.Face.FACE_5
    }
  }

  private fun mapHairstyle(hair: Hairstyle): MasterProto.Hairstyle {
    return when (hair) {
      Hairstyle.HAIR_1 -> MasterProto.Hairstyle.HAIR_1
      Hairstyle.HAIR_2 -> MasterProto.Hairstyle.HAIR_2
      Hairstyle.HAIR_3 -> MasterProto.Hairstyle.HAIR_3
      Hairstyle.HAIR_4 -> MasterProto.Hairstyle.HAIR_4
      Hairstyle.HAIR_5 -> MasterProto.Hairstyle.HAIR_5
    }
  }
}