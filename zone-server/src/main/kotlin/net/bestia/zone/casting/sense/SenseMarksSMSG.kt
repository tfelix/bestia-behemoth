package net.bestia.zone.casting.sense

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SenseMarksSmsgProto
import net.bestia.zone.message.SMSG

data class SenseMarksSMSG(val marks: List<SenseMark>, val visibleSeconds: Int) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = SenseMarksSmsgProto.SenseMarksSMSG.newBuilder().setVisibleSeconds(visibleSeconds)
    for (mark in marks) {
      proto.addMarks(
        SenseMarksSmsgProto.SenseMark.newBuilder()
          .setKind(SenseMarksSmsgProto.SenseKind.forNumber(mark.kind.ordinal))
          .setEastMetres(mark.eastMetres)
          .setNorthMetres(mark.northMetres)
          .setLabel(mark.label)
          .setRefId(mark.refId)
          .setDepthMetres(mark.depthMetres.toFloat())
          .setSizeMetres(mark.sizeMetres.toFloat())
          .setVague(mark.vague)
      )
    }

    return EnvelopeProto.Envelope.newBuilder().setSenseMarks(proto).build()
  }
}
