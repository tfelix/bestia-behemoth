package net.bestia.zone.cartography.survey

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SurveyMinigameSmsgProto
import net.bestia.zone.message.SMSG

data class SurveyMinigameSMSG(val structureId: Long, val game: SurveyMinigame) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = SurveyMinigameSmsgProto.SurveyMinigameSMSG.newBuilder()
      .setStructureId(structureId)
      .setSwingPeriodMs(game.swingPeriodMs)
      .setArcWidthDegrees(game.arcWidthDegrees)
      .addAllArcCentresDegrees(game.arcCentresDegrees)
      .build()

    return EnvelopeProto.Envelope.newBuilder().setSurveyMinigame(proto).build()
  }
}
