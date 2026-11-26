package net.bestia.zone.doomsday

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.doomsday.persistence.StoredWorldEnd
import net.bestia.zone.doomsday.persistence.StoredWorldEndRepository
import net.bestia.zone.doomsday.persistence.WorldLegacy
import net.bestia.zone.doomsday.persistence.WorldLegacyRepository
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.AccountConnectedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.WorldRepository
import net.bestia.zone.world.settlement.SettlementFates
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The last day of a world: once its fate is decided, a Last Stand or the Calling runs for a day with a countdown.
 * When it closes, every client plays its final sequence, the end goes on record and the server restarts into a
 * new world.
 */
@Service
class WorldEnd(
  private val config: DoomsdayConfig,
  private val director: DoomsdayDirector,
  private val windows: StoredWorldEndRepository,
  private val legacies: WorldLegacyRepository,
  private val worlds: WorldRepository,
  private val worldService: WorldService,
  private val fates: SettlementFates,
  private val out: OutMessageProcessor,
  private val events: ApplicationEventPublisher,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Null while no window is open, and unknown until the first run reads it. */
  @Volatile
  private var window: StoredWorldEnd? = null
  private var windowRead = false

  /** The final sequence once it plays, for masters who log in during it. */
  @Volatile
  private var sequence: WorldEndSMSG? = null

  /** Transactional, so the end record commits before anyone is sent away. */
  @Scheduled(fixedDelay = RUN_EVERY_MS, initialDelay = RUN_EVERY_MS)
  @Transactional
  @Synchronized
  fun run() {
    if (!worldService.isLoaded || hasEnded()) {
      return
    }
    val open = openWindow() ?: openByFate() ?: return
    val left = Duration.between(clock.instant(), open.endsAt)

    when {
      left <= Duration.ZERO -> finish(open)
      left.seconds <= config.endSequenceSeconds -> playSequence(open)
      else -> warn(open, left)
    }
  }

  /** Opens a window that ends the world after [lasting], whatever the doom clock says. For a GM. */
  @Transactional
  @Synchronized
  fun open(ending: DoomEnding, lasting: Duration): StoredWorldEnd {
    val opened = windows.save(StoredWorldEnd(ending = ending, endsAt = clock.instant() + lasting))
    window = opened
    windowRead = true
    sequence = null
    LOG.warn { "The world's end is decided: $ending at ${opened.endsAt}" }

    warn(opened, lasting)
    return opened
  }

  /**
   * Closes the window without an end, and answers which one it was. A Last Stand counts as won: the world is pushed
   * back from the brink. A Calling opens again on the next run while the world is past its cap.
   */
  @Transactional
  @Synchronized
  fun beatBack(): DoomEnding? {
    val open = openWindow() ?: return null
    windows.delete(open)
    window = null
    sequence = null

    if (open.ending == DoomEnding.LOST) {
      director.set(config.lastStandPushBackTo)
      out.sendToAllConnected(AnnouncementSMSG(Announcement.LAST_STAND_WON))
    }
    LOG.warn { "The world's end was beaten back: ${open.ending}" }
    return open.ending
  }

  /** A master who logs in during the final sequence sees it too. */
  @EventListener
  fun onConnected(event: AccountConnectedEvent) {
    val playing = sequence ?: return
    val open = window ?: return
    out.sendToPlayer(event.accountId, playing.copy(secondsLeft = secondsLeft(open)))
  }

  private fun openByFate(): StoredWorldEnd? {
    if (!config.enabled) {
      return null
    }
    val ending = director.ending() ?: return null
    return open(ending, Duration.ofMinutes((config.endWindowHours * 60).roundToInt().toLong()))
  }

  private fun openWindow(): StoredWorldEnd? {
    if (!windowRead) {
      window = windows.findById(StoredWorldEnd.ONLY).orElse(null)
      windowRead = true
    }
    return window
  }

  /** Announces the countdown at the first sight of the window, then once at each of [WARNINGS]. */
  private fun warn(open: StoredWorldEnd, left: Duration) {
    val minutesLeft = ceil(left.seconds / 60.0).toInt()
    val warnedAt = open.warnedAtMinutes
    if (warnedAt != null && WARNINGS.none { it < warnedAt && minutesLeft <= it }) {
      return
    }

    out.sendToAllConnected(countdownOf(open.ending, minutesLeft))
    open.warnedAtMinutes = WARNINGS.filter { it >= minutesLeft }.minOrNull() ?: minutesLeft
    windows.save(open)
  }

  private fun countdownOf(ending: DoomEnding, minutesLeft: Int): AnnouncementSMSG {
    val inHours = minutesLeft >= HOURS_FROM_MINUTES
    val kind = when (ending) {
      DoomEnding.LOST -> if (inHours) Announcement.LAST_STAND else Announcement.LAST_STAND_SOON
      DoomEnding.SAVED -> if (inHours) Announcement.CALLING else Announcement.CALLING_SOON
    }
    val amount = if (inHours) (minutesLeft / 60.0).roundToInt() else minutesLeft

    return AnnouncementSMSG(kind, listOf(amount.toString()))
  }

  private fun playSequence(open: StoredWorldEnd) {
    if (sequence != null) {
      return
    }
    val playing = summaryOf(open)
    sequence = playing
    val told = out.sendToAllConnected(playing)
    LOG.warn { "Playing the world's final sequence for $told connection(s)" }
  }

  private fun finish(open: StoredWorldEnd) {
    playSequence(open)
    val summary = summaryOf(open)
    val world = worldService.record
    val now = clock.instant()

    legacies.save(
      WorldLegacy(
        worldName = summary.worldName,
        seed = world.seed,
        threat = summary.threat,
        ending = summary.ending,
        daysLived = summary.daysLived,
        tPercent = summary.tPercent,
        townsLost = summary.townsLost,
        bornAt = world.createdAt,
        endedAt = now,
      )
    )
    world.endedAt = now
    worlds.save(world)
    LOG.warn { "The world ${summary.worldName} has ended: $summary" }

    events.publishEvent(WorldEndedEvent(open.ending))
  }

  private fun summaryOf(open: StoredWorldEnd): WorldEndSMSG {
    val world = worldService.record
    val status = director.status()

    return WorldEndSMSG(
      ending = open.ending,
      worldName = world.name,
      threat = status.kind,
      daysLived = Duration.between(world.createdAt, clock.instant()).toDays().toInt(),
      tPercent = status.tPercent.roundToInt(),
      townsLost = fates.fallen().size,
      secondsLeft = secondsLeft(open),
    )
  }

  private fun secondsLeft(open: StoredWorldEnd): Int {
    return Duration.between(clock.instant(), open.endsAt).seconds.coerceAtLeast(0).toInt()
  }

  /** An ended world waits for the restart that replaces it. */
  private fun hasEnded(): Boolean {
    return worldService.record.endedAt != null
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val RUN_EVERY_MS = 10_000L

    /** Countdown marks in minutes. The final sequence takes over after the last one. */
    val WARNINGS = listOf(24 * 60, 60, 10)

    /** Shorter countdowns are told in minutes. */
    const val HOURS_FROM_MINUTES = 120
  }
}
