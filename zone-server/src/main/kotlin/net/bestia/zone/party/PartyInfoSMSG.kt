package net.bestia.zone.party

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.util.EntityId
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInfoSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass

data class PartyInfoSMSG(
  val partyId: Long,
  val partyName: String,
  val member: List<PartyMember>
) : SMSG {

  data class PartyMember(
    val masterName: String,
    val onlineData: OnlineData?
  ) {

    data class OnlineData(
      val entityId: EntityId,
      val areaName: String,
      val position: Vec3L,
      val hp: CurMax
    )
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoMembers = member.map { partyMember ->
      val builder = PartyInfoSmsgProto.PartyMember.newBuilder()
        .setMasterName(partyMember.masterName)

      val onlineData = partyMember.onlineData
      if (onlineData != null) {
        val position = Vec3OuterClass.Vec3.newBuilder()
          .setX(onlineData.position.x)
          .setY(onlineData.position.y)
          .setZ(onlineData.position.z)
          .build()

        builder.setOnlineData(
          PartyInfoSmsgProto.PartyMemberOnlineData.newBuilder()
            .setEntityId(onlineData.entityId)
            .setAreaName(onlineData.areaName)
            .setPosition(position)
            .setHpCurrent(onlineData.hp.current)
            .setHpMax(onlineData.hp.max)
        )
      }

      builder.build()
    }

    val proto = PartyInfoSmsgProto.PartyInfoSMSG.newBuilder()
      .setPartyId(partyId)
      .setPartyName(partyName)
      .addAllMember(protoMembers)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInfo(proto)
      .build()
  }
}