package net.bestia.zone.bestia.diary

import net.bestia.bnet.proto.BestiaDiarySmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG

/** The diary of a master's bestias, newest first, for the chat's Bestias tab. */
data class BestiaDiarySMSG(
  val lines: List<BestiaNewsSMSG>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val diary = BestiaDiarySmsgProto.BestiaDiarySMSG.newBuilder()
      .addAllLines(lines.map { it.toBnetEnvelope().bestiaNews })

    return EnvelopeProto.Envelope.newBuilder()
      .setBestiaDiary(diary)
      .build()
  }
}
