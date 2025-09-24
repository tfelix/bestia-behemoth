package net.bestia.zone.party

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.status.CurMax
import net.bestia.zone.util.EntityId
import net.bestia.bnet.proto.EnvelopeProto

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
    TODO("Not yet implemented")
  }
}