package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.bestia.diary.AwayReport
import net.bestia.zone.bestia.diary.BestiaDiary
import net.bestia.zone.master.bestia.AwayReports
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Service

/** What a master's bestias did lately, for the companion app's diary and away report. */
@Service
class DiaryQueries(
  private val owned: OwnedMasters,
  private val diary: BestiaDiary,
  private val awayReports: AwayReports,
  private val connections: ConnectionInfoService,
) {

  fun diary(accountId: Long, masterId: Long): ZoneAnswer.Diary? {
    owned.find(accountId, masterId) ?: return null

    return ZoneAnswer.Diary(
      diary.of(masterId).map { line ->
        ZoneAnswer.Diary.DiaryEntry(
          line.playerBestiaId, line.at.toEpochMilli(), line.kind.name, line.itemAmounts(), line.place, line.reason,
        )
      }
    )
  }

  fun awayReport(accountId: Long, masterId: Long): ZoneAnswer.AwayReport? {
    owned.find(accountId, masterId) ?: return null
    val report = awayReports.of(masterId) ?: return null

    return ZoneAnswer.AwayReport(
      sinceEpochMs = report.since.toEpochMilli(),
      masterInWorld = connections.isPlaying(accountId, masterId),
      bestias = report.bestias.map(::bestiaReportOf),
    )
  }

  private fun bestiaReportOf(report: AwayReport): ZoneAnswer.AwayReport.BestiaReport {
    return ZoneAnswer.AwayReport.BestiaReport(
      playerBestiaId = report.playerBestiaId,
      name = report.name,
      species = report.species,
      brought = report.brought,
      broughtTo = report.broughtTo,
      madeAt = report.madeAt,
      timeIn = report.timeIn.mapKeys { (state, _) -> state.name },
      incidents = report.incidents.map { ZoneAnswer.AwayReport.Incident(it.kind.name, it.at.toEpochMilli(), it.reason, it.place) },
      now = report.now,
      nowAt = report.nowAt,
      blocker = report.blocker?.name,
      hint = report.hint?.name,
    )
  }
}
