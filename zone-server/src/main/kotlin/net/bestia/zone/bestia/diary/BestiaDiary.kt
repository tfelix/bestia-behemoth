package net.bestia.zone.bestia.diary

import net.bestia.zone.bestia.persistence.DiaryLineRepository
import net.bestia.zone.bestia.persistence.DiaryLine
import net.bestia.zone.battle.ecs.damage.OwnedBestiaDiedEvent
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** Writes what owned bestias did into their diary, and keeps it from growing past [DiaryConfig]. */
@Service
class BestiaDiary(
  private val lines: DiaryLineRepository,
  private val jobs: AsyncJobExecutor,
  private val config: DiaryConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  @EventListener
  fun onHappened(happened: BestiaHappened) {
    val line = DiaryLine(
      masterId = happened.masterId,
      playerBestiaId = happened.playerBestiaId,
      at = clock.instant(),
      kind = happened.kind,
      items = DiaryLine.itemsOf(happened.items),
      place = happened.place,
      reason = happened.reason,
    )
    jobs.submit(happened.masterId) { record(line) }
  }

  @EventListener
  fun onDied(died: OwnedBestiaDiedEvent) {
    val kind = if (died.lostForGood) BestiaHappened.Kind.LOST else BestiaHappened.Kind.DIED
    onHappened(BestiaHappened(this, died.masterId, died.playerBestiaId, kind))
  }

  /** The master's diary, newest first. */
  @Transactional(readOnly = true)
  fun of(masterId: Long): List<DiaryLine> = lines.findAllByMasterIdOrderByAtDesc(masterId)

  @Transactional
  fun record(line: DiaryLine) {
    if (mergedIntoLast(line)) return

    lines.save(line)
    val excess = lines.countByPlayerBestiaId(line.playerBestiaId) - config.maxLinesPerBestia
    if (excess > 0) {
      lines.deleteAll(lines.findAllByPlayerBestiaIdOrderByAtDesc(line.playerBestiaId).takeLast(excess.toInt()))
    }
  }

  /** A haul soon after the last one adds to its line, so the diary sums the goods per hour instead of per trip. */
  private fun mergedIntoLast(line: DiaryLine): Boolean {
    val window = line.kind.mergesWithin ?: return false
    val last = lines.findFirstByPlayerBestiaIdAndKindOrderByAtDesc(line.playerBestiaId, line.kind) ?: return false
    if (last.place != line.place || Duration.between(last.at, line.at) > window) return false

    val summed = last.itemAmounts().toMutableMap()
    line.itemAmounts().forEach { (identifier, amount) -> summed.merge(identifier, amount, Int::plus) }
    val items = DiaryLine.itemsOf(summed)
    if ((items?.length ?: 0) > DiaryLine.ITEMS_LENGTH) return false

    last.items = items
    lines.save(last)
    return true
  }

  @Scheduled(fixedDelay = SWEEP_MILLIS, initialDelay = SWEEP_MILLIS)
  @Transactional
  fun dropOldLines() {
    lines.deleteOlderThan(clock.instant() - Duration.ofDays(config.keepDays))
  }

  private companion object {
    const val SWEEP_MILLIS = 60 * 60 * 1000L
  }
}
