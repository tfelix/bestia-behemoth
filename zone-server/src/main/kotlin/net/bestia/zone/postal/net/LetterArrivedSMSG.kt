package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LetterArrivedSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.LetterView

data class LetterArrivedSMSG(
  val letter: LetterView,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setLetterArrived(LetterArrivedSmsgProto.LetterArrivedSMSG.newBuilder().setLetter(letter.toProto()))
      .build()
  }
}
