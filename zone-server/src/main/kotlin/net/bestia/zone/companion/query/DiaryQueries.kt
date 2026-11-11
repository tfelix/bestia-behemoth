package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.bestia.diary.BestiaDiary
import org.springframework.stereotype.Service

/** What a master's bestias did lately, for the companion app's diary. */
@Service
class DiaryQueries(
  private val owned: OwnedMasters,
  private val diary: BestiaDiary,
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
}
