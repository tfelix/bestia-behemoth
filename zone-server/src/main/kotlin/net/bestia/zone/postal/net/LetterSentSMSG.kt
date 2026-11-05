package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LetterSentSmsgProto
import net.bestia.zone.message.SMSG

data class LetterSentSMSG(
  val recipientName: String,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setLetterSent(LetterSentSmsgProto.LetterSentSMSG.newBuilder().setRecipientName(recipientName))
      .build()
  }
}
