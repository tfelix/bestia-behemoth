package net.bestia.zone.settlement

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SettlementPlannedSmsgProto
import net.bestia.zone.message.SMSG

data class SettlementPlannedSMSG(val name: String) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val planned = SettlementPlannedSmsgProto.SettlementPlannedSMSG.newBuilder()
      .setName(name)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSettlementPlanned(planned)
      .build()
  }
}
