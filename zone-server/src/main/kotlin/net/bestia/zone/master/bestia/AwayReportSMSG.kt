package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.AwayReportSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.bestia.diary.AwayReport
import net.bestia.zone.message.SMSG

data class AwayReportSMSG(val report: AwayReports.Report) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val away = AwayReportSmsgProto.AwayReportSMSG.newBuilder()
      .setSinceEpochMs(report.since.toEpochMilli())
    report.bestias.forEach { away.addBestias(bestiaOf(it)) }

    return EnvelopeProto.Envelope.newBuilder()
      .setAwayReport(away)
      .build()
  }

  private fun bestiaOf(bestia: AwayReport): AwayReportSmsgProto.BestiaAwayReport {
    val wire = AwayReportSmsgProto.BestiaAwayReport.newBuilder()
      .setPlayerBestiaId(bestia.playerBestiaId)
      .setName(bestia.name.orEmpty())
      .setSpecies(bestia.species)
      .putAllBrought(bestia.brought)
      .addAllBroughtTo(bestia.broughtTo)
      .putAllMadeAt(bestia.madeAt)
      .putAllTimeIn(bestia.timeIn.mapKeys { (state, _) -> state.name })
      .setNow(bestia.now)
      .setNowAt(bestia.nowAt.orEmpty())
      .setBlocker(bestia.blocker?.name.orEmpty())
      .setHint(bestia.hint?.name.orEmpty())
    bestia.incidents.forEach { incident ->
      wire.addIncidents(
        AwayReportSmsgProto.BestiaIncident.newBuilder()
          .setKind(incident.kind.name)
          .setAtEpochMs(incident.at.toEpochMilli())
          .setReason(incident.reason.orEmpty())
          .setPlace(incident.place.orEmpty())
      )
    }

    return wire.build()
  }
}
