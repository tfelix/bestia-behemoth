package net.bestia.zone.doomsday

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.doomsday.persistence.StoredDoomsdayRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.doomsday.foreshadow.ForeshadowRunner
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.Locale
import kotlin.random.Random

/**
 * Runs the doom clock of the current world: decides its fate on first sight, then every few minutes lets the
 * threat act and the clock advance at the pace the players set. Off the tick, like `WorldEventDirector`.
 */
@Service
class DoomsdayDirector(
  private val config: DoomsdayConfig,
  private val doomsdays: StoredDoomsdayRepository,
  private val worldService: WorldService,
  threatBeans: ObjectProvider<DoomThreat>,
  private val signs: DoomsdaySigns,
  private val events: ApplicationEventPublisher,
  private val foreshadow: ForeshadowRunner,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val threats: List<DoomThreat> by lazy { threatBeans.orderedStream().toList() }
  private val doomClock = DoomClock(config)

  @Scheduled(fixedDelay = RUN_EVERY_MS, initialDelay = RUN_EVERY_MS)
  @Transactional
  fun run() {
    if (!config.enabled || !worldService.isLoaded) {
      return
    }
    runAt(ageDays())
  }

  /** One run with the world at [ageDays]. */
  fun runAt(ageDays: Double) {
    val row = current()
    val threat = threatOf(row)
    val before = stateOf(row)
    val after = doomClock.advance(before, row.onsetDay, row.unfoldDays, row.steppedToDay, ageDays, paceOf(row, threat))
    val realSeconds = (ageDays - row.steppedToDay).coerceAtLeast(0.0) / config.timeScale * SECONDS_PER_DAY

    row.steppedToDay = maxOf(row.steppedToDay, ageDays)
    row.onsetDay += foreshadow.run(row, ageDays, realSeconds, worldService.record.seed)
    apply(row, after, threat)
    val x = row.originX
    val y = row.originY
    if (threat != null && x != null && y != null && ageDays >= row.onsetDay) {
      val pushed = threat.step(DoomFate(x, y, row.progress, row.stage), realSeconds)
      if (pushed != 0.0) {
        apply(row, doomClock.push(stateOf(row), pushed), threat)
      }
    }
    save(row)
  }

  @Transactional
  fun status(): DoomsdayStatus {
    val row = current()
    val threat = threatOf(row)
    val ageDays = ageDays()
    val pace = paceOf(row, threat)

    return DoomsdayStatus(
      kind = row.kind,
      originX = row.originX,
      originY = row.originY,
      stage = row.stage,
      progress = row.progress,
      pace = pace,
      paceOverridden = row.paceOverride != null,
      pressure = threat?.pressure() ?: 0.0,
      containment = threat?.containment() ?: 0.0,
      ageDays = ageDays,
      onsetDay = row.onsetDay,
      lifeSpanDays = row.lifeSpanDays,
      tPercent = doomClock.tPercent(ageDays, row.lifeSpanDays),
      projectedEndDay = maxOf(ageDays, row.onsetDay) + (1.0 - row.progress) * row.unfoldDays / pace,
      foreshadowKind = row.foreshadowKind,
      foreshadowDay = row.foreshadowDay,
      ending = doomClock.ending(stateOf(row), ageDays, row.lifeSpanDays),
    )
  }

  /** How the world ends now, or null while it goes on. */
  @Transactional
  fun ending(): DoomEnding? {
    val row = current()
    return doomClock.ending(stateOf(row), ageDays(), row.lifeSpanDays)
  }

  /** Tells the threat the Last Stand opened, so it raises its Avatar. */
  @Transactional
  fun lastStandOpened() {
    val row = current()
    val x = row.originX ?: return
    val y = row.originY ?: return
    threatOf(row)?.onLastStand(DoomFate(x, y, row.progress, row.stage))
  }

  /** Moves the clock by [delta], for a deed or a GM. */
  @Transactional
  fun push(delta: Double): DoomStage {
    return change { row -> doomClock.push(stateOf(row), delta) }
  }

  /** Sets the clock to [progress], for a GM. */
  @Transactional
  fun set(progress: Double): DoomStage {
    return change { row -> doomClock.set(stateOf(row), progress) }
  }

  /** Fixes the pace at [pace], or gives it back to the threat with null. For a GM. */
  @Transactional
  fun overridePace(pace: Double?) {
    val row = current()
    row.paceOverride = pace?.coerceIn(0.0, config.maxPace)
    save(row)
  }

  /** Gives the world another threat and starts its clock over. For alpha tests; false when [kind] cannot run here. */
  @Transactional
  fun repick(kind: DoomKind): Boolean {
    val threat = threats.firstOrNull { it.kind == kind && it.fits() } ?: return false
    val origin = threat.origin(Random(clock.millis()))
    val row = current()

    row.kind = kind
    row.originX = origin.x
    row.originY = origin.y
    row.progress = 0.0
    row.stage = DoomStage.OMENS
    save(row)
    LOG.info { "Doomsday changed to $kind at (${origin.x}, ${origin.y})" }
    return true
  }

  /** Gives the world another Foreshadowing. For alpha tests; false when [kind] is not built. */
  @Transactional
  fun repickForeshadow(kind: ForeshadowKind): Boolean {
    if (kind !in foreshadow.built()) {
      return false
    }
    val row = current()
    row.foreshadowKind = kind
    foreshadow.forget()
    save(row)
    return true
  }

  /** Lets the Foreshadowing strike on the next run, also if it struck before. For alpha tests. */
  @Transactional
  fun foreshadowNow() {
    val row = current()
    foreshadow.forget()
    row.foreshadowDay = ageDays()
    row.onsetDay = maxOf(row.onsetDay, row.foreshadowDay + config.foreshadowMaxDays)
    save(row)
  }

  /** Ends the running Foreshadowing now. For a GM; false when none runs. */
  @Transactional
  fun endForeshadow(contained: Boolean): Boolean {
    val shift = foreshadow.end(contained) ?: return false
    val row = current()
    row.onsetDay += shift
    save(row)
    return true
  }

  fun builtKinds(): List<DoomKind> {
    return threats.map { it.kind }.sorted()
  }

  /** The world's age for the doomsday, in days, sped up on test servers. */
  fun ageDays(): Double {
    val age = Duration.between(worldService.record.createdAt, clock.instant())
    return age.toMillis() / 1000.0 / SECONDS_PER_DAY * config.timeScale
  }

  private fun current(): StoredDoomsday {
    val row = doomsdays.findById(StoredDoomsday.ONLY).orElseGet {
      val picked = DoomsdayPicker(config, threats, foreshadow.built()).pick(worldService.record.seed)
      LOG.info {
        "The world's fate: ${picked.kind ?: "no threat"} sets in on day ${picked.onsetDay.toInt()} and unfolds over " +
          "${picked.unfoldDays.toInt()} days; Foreshadowing ${picked.foreshadowKind ?: "none"} on day " +
          "${picked.foreshadowDay.toInt()}"
      }
      save(picked)
    }
    signs.remember(row)
    return row
  }

  private fun save(row: StoredDoomsday): StoredDoomsday {
    val saved = doomsdays.save(row)
    signs.remember(saved)
    return saved
  }

  private fun change(next: (StoredDoomsday) -> DoomClock.State): DoomStage {
    val row = current()
    apply(row, next(row), threatOf(row))
    save(row)
    return row.stage
  }

  private fun apply(row: StoredDoomsday, state: DoomClock.State, threat: DoomThreat?) {
    val from = row.stage
    row.progress = state.progress
    row.stage = state.stage

    if (from != state.stage) {
      val progress = String.format(Locale.ROOT, "%.3f", state.progress)
      LOG.info { "Doomsday ${row.kind} moved from $from to ${state.stage} at $progress" }
      threat?.onStage(from, state.stage)
      val kind = row.kind
      val x = row.originX
      val y = row.originY
      if (kind != null && x != null && y != null) {
        events.publishEvent(DoomStageChanged(kind, x, y, from, state.stage))
      }
    }
  }

  private fun paceOf(row: StoredDoomsday, threat: DoomThreat?): Double {
    return row.paceOverride ?: doomClock.paceOf(threat?.pressure() ?: 0.0, threat?.containment() ?: 0.0)
  }

  private fun stateOf(row: StoredDoomsday): DoomClock.State {
    return DoomClock.State(row.progress, row.stage)
  }

  private fun threatOf(row: StoredDoomsday): DoomThreat? {
    return threats.firstOrNull { it.kind == row.kind }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val RUN_EVERY_MS = 5 * 60 * 1000L
    const val SECONDS_PER_DAY = 86_400.0
  }
}
