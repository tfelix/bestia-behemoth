package net.bestia.zone.master

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MasterProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import java.awt.Color
import net.bestia.bnet.proto.BestiaInfoProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.SelfSMSG
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.townsfolk.conversation.Line
import net.bestia.zone.townsfolk.conversation.toBnet

data class AvailableMasterSMSG(
  val master: List<MasterInfo>,
  val maxAvailableMasterSlots: Int,
  val maxAvailableBestiaSlots: Int,
  /** Settlement spawn point candidates a new master can choose to start life near. */
  val spawnPoints: List<SpawnPointCandidate> = emptyList()
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
    /** The account slot the master is pinned to, counted from 0. See [net.bestia.zone.account.persistence.Master.slot]. */
    val slot: Int,
    val bestias: List<SelfSMSG.BestiaInfo>
  )

  data class SpawnPointCandidate(
    val id: Int,
    val settlementName: String,
    val tier: String,
    val population: Int,
    val position: Vec3L,
    /** See [HomeFacts]. */
    val biomeToken: String,
    val history: List<Line>,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val masterBuilder = MasterProto.Master.newBuilder()
      .setMaxAvailableMasterSlots(maxAvailableMasterSlots)
      .setMaxAvailableBestiaSlots(maxAvailableBestiaSlots)

    spawnPoints.forEach { candidate ->
      masterBuilder.addSpawnPoints(
        MasterProto.MasterSpawnPointCandidate.newBuilder()
          .setId(candidate.id)
          .setSettlementName(candidate.settlementName)
          .setTier(candidate.tier)
          .setPopulation(candidate.population)
          .setX(candidate.position.x)
          .setY(candidate.position.y)
          .setBiomeToken(candidate.biomeToken)
          .addAllHistory(candidate.history.map { it.toBnet() })
          .build()
      )
    }

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
        .setSlot(masterInfo.slot)
        .setPosition(position)
        .setBody(mapBodyType(masterInfo.body))
        .setSkinColor(skinColor)
        .setHairColor(hairColor)
        .setFace(mapFace(masterInfo.face))
        .setHair(mapHairstyle(masterInfo.hair))

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
      BodyType.BODY_F_1 -> MasterProto.BodyType.BODY_F_1
    }
  }

  private fun mapFace(face: Face): MasterProto.Face {
    return when (face) {
      Face.FACE_1 -> MasterProto.Face.FACE_1
      Face.FACE_2 -> MasterProto.Face.FACE_2
      Face.FACE_3 -> MasterProto.Face.FACE_3
      Face.FACE_4 -> MasterProto.Face.FACE_4
      Face.FACE_5 -> MasterProto.Face.FACE_5
      Face.FACE_6 -> MasterProto.Face.FACE_6
      Face.FACE_7 -> MasterProto.Face.FACE_7
      Face.FACE_8 -> MasterProto.Face.FACE_8
      Face.FACE_9 -> MasterProto.Face.FACE_9
      Face.FACE_10 -> MasterProto.Face.FACE_10
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