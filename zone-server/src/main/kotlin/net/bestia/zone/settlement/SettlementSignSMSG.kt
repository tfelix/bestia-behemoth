package net.bestia.zone.settlement

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SettlementSignSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class SettlementSignSMSG(
  val signEntityId: EntityId,
  val townId: Long,
  val name: String,
  val founderName: String,
  val isFounder: Boolean,
  val radiusMetres: Int,
  val buildingPointsTenths: Int,
  val signDefense: Int,
  val canTearDown: Boolean,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val sign = SettlementSignSmsgProto.SettlementSignSMSG.newBuilder()
      .setSignEntityId(signEntityId)
      .setSettlementId(townId)
      .setName(name)
      .setFounderName(founderName)
      .setIsFounder(isFounder)
      .setRadiusMetres(radiusMetres)
      .setBuildingPointsTenths(buildingPointsTenths)
      .setSignDefense(signDefense)
      .setCanTearDown(canTearDown)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSettlementSign(sign)
      .build()
  }
}
