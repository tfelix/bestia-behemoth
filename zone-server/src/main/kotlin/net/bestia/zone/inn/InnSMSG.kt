package net.bestia.zone.inn

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.InnSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class InnSMSG(
  val counterEntityId: EntityId,
  val placeName: String,
  val savePointHere: Boolean,
  val tendings: List<InnTending.Line>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val inn = InnSmsgProto.InnSMSG.newBuilder()
      .setCounterEntityId(counterEntityId)
      .setPlaceName(placeName)
      .setSavePointHere(savePointHere)
    tendings.forEach { line ->
      inn.addTendings(
        InnSmsgProto.InnSMSG.Tending.newBuilder()
          .setEntityId(line.entityId)
          .setName(line.name.orEmpty())
          .setBestiaId(line.bestiaId)
          .setPrice(line.price)
          .setFallen(line.fallen)
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setInn(inn)
      .build()
  }
}
