package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LettersSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.LetterView

data class LettersSMSG(
  val letters: List<LetterView>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setLetters(LettersSmsgProto.LettersSMSG.newBuilder().addAllLetters(letters.map { it.toProto() }))
      .build()
  }
}
