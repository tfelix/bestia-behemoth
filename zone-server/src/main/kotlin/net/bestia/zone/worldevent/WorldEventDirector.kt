package net.bestia.zone.worldevent

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.GenRng
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.EventProvince
import net.bestia.zone.worldevent.persistence.EventProvinceRepository
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import net.bestia.zone.worldevent.persistence.WorldEventInstance.Outcome
import net.bestia.zone.worldevent.persistence.WorldEventInstance.State
import net.bestia.zone.worldevent.persistence.WorldEventInstanceRepository
import net.bestia.zone.worldevent.contract.ContractRewards
import net.bestia.zone.worldevent.contract.ContributionJournal
import net.bestia.zone.worldevent.contract.ContributionLedger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Picks the discrete world events, one province at a time. What follows the mana on its own - storms, stronger
 * dens, sickness - is not decided here.
 *
 * Every run it lets each province's budget grow, ends the events that are over and gives each ready province a
 * chance to start one it can afford. Rarity is the whole design: a budget that grows slowly, a quiet after each
 * event and a chance per run, so a place cannot be farmed for events.
 *
 * Off the tick; on a cluster a single node holds this job, as with `QuestDirector`.
 */
@Service
class WorldEventDirector(
  private val catalogue: WorldEventCatalogue,
  private val snapshot: ProvinceSignalSnapshot,
  private val provinces: ProvinceService,
  private val provinceRows: EventProvinceRepository,
  private val instances: WorldEventInstanceRepository,
  private val worldService: WorldService,
  private val config: WorldEventConfig,
  private val clock: BestiaClock,
  private val ledger: ContributionLedger,
  private val rewards: ContractRewards,
  private val journal: ContributionJournal,
  private val runningEvents: RunningEvents? = null,
) {

  @Scheduled(fixedDelay = RUN_EVERY_MS, initialDelay = RUN_EVERY_MS)
  @Transactional
  fun run() {
    if (!config.enabled || !worldService.isLoaded) return
    runAt(clock.now().absoluteSecond)
  }

  /** One run at Bestia second [now]. */
  fun runAt(now: Long) {
    val rows = provinceRows()
    rows.values.forEach { accrue(it, now) }

    val running = instances.findAllByStateIn(RUNNING).filterNot { settle(it, rows, now) }
    val busy = running.map { it.provinceIndex }.toSet()
    holdGround(running)

    snapshot.take()
      .filter { !it.isSanctuary && it.province.index !in busy }
      .forEach { signals -> maybeStart(signals, rows.getValue(signals.province.index), now) }

    journal.save()
    // Positions are voxels, which are metres.
    runningEvents?.replace(running().map { it.x.toDouble() to it.y.toDouble() })
  }

  /** Starts [entry] where [signals] say, whatever the budget, the quiet or the mana. For a GM. */
  @Transactional
  fun force(entry: WorldEventEntry, signals: ProvinceSignals): WorldEventInstance? {
    val now = clock.now().absoluteSecond
    val row = provinceRows().getValue(signals.province.index)
    return start(entry, signals, row, now)
  }

  /** Ends a running event at once and takes back what it put into the world. For a GM. */
  @Transactional
  fun cancel(instanceId: Long): Boolean {
    val instance = instances.findById(instanceId).orElse(null) ?: return false
    if (instance.state == State.RESOLVED) return false

    catalogue.of(instance.eventId)?.let { catalogue.eventOf(it).end(instance) }
    resolve(instance, Outcome.CANCELLED, provinceRows(), clock.now().absoluteSecond)
    journal.save()
    return true
  }

  fun running(): List<WorldEventInstance> = instances.findAllByStateIn(RUNNING)

  fun budgetOf(provinceIndex: Int): Double = provinceRows()[provinceIndex]?.budget ?: 0.0

  private fun maybeStart(signals: ProvinceSignals, row: EventProvince, now: Long) {
    if (now < row.quietUntilSecond) return

    val candidates = catalogue.all().filter { it.cost <= row.budget && fits(it, signals, now) }
    if (candidates.isEmpty()) return

    val random = Random(GenRng.hash(worldService.config.seed, signals.province.index.toLong(), now / ROLL_WINDOW_SECONDS))
    if (random.nextDouble() >= config.startChance) return

    start(pickWeighted(candidates, random), signals, row, now)
  }

  private fun fits(entry: WorldEventEntry, signals: ProvinceSignals, now: Long): Boolean {
    if (signals.cellsAtLeast(entry.minTier) < entry.minTierCells) return false

    val last = instances.findFirstByProvinceIndexAndEventIdOrderByStartedAtSecondDesc(signals.province.index, entry.id)
    if (last != null && now - last.startedAtSecond < entry.cooldown * SECONDS_PER_DAY) return false

    return catalogue.eventOf(entry).fits(signals)
  }

  private fun start(entry: WorldEventEntry, signals: ProvinceSignals, row: EventProvince, now: Long): WorldEventInstance? {
    val started = catalogue.eventOf(entry).start(signals) ?: return null

    row.budget = (row.budget - entry.cost).coerceAtLeast(0.0)
    val instance = instances.save(
      WorldEventInstance(
        eventId = entry.id,
        provinceIndex = signals.province.index,
        partitionVersion = row.partitionVersion,
        state = State.ACTIVE,
        startedAtSecond = now,
        endsAtSecond = now + (entry.lasts * SECONDS_PER_DAY).toLong(),
        x = started.at.x,
        y = started.at.y,
        z = started.at.z,
        subjectId = started.subjectId,
      )
    )

    LOG.info { "Started ${entry.id} in ${signals.province.name} at ${started.at}" }
    return instance
  }

  /** @return true when the event is over */
  private fun settle(instance: WorldEventInstance, rows: Map<Int, EventProvince>, now: Long): Boolean {
    val event = catalogue.of(instance.eventId)?.let(catalogue::eventOf)

    val outcome = when {
      event == null -> Outcome.CANCELLED
      event.isDefeated(instance) -> Outcome.DEFEATED
      event.isOver(instance) -> Outcome.EXPIRED
      now >= instance.endsAtSecond -> Outcome.EXPIRED.also { event.end(instance) }
      else -> return false
    }

    resolve(instance, outcome, rows, now)
    return true
  }

  private fun resolve(instance: WorldEventInstance, outcome: Outcome, rows: Map<Int, EventProvince>, now: Long) {
    instance.state = State.RESOLVED
    instance.outcome = outcome
    rows[instance.provinceIndex]?.quietUntilSecond = now + (config.quietBestiaDays * SECONDS_PER_DAY).toLong()

    val entry = catalogue.of(instance.eventId)
    val key = entry?.let { keyOf(it, instance) }
    if (key != null) {
      // Only an event players ended pays; one that ran out or was called off leaves its contributors nothing.
      if (outcome == Outcome.DEFEATED) rewards.grant(key, entry, instance) else ledger.forget(key)
    }

    LOG.info { "World event ${instance.eventId} in province ${instance.provinceIndex} ended: $outcome" }
  }

  /** Every master standing on the ground of a running event holds it for another run. */
  private fun holdGround(running: List<WorldEventInstance>) {
    if (running.isEmpty()) return
    val masters = snapshot.masters()

    running.forEach { instance ->
      val entry = catalogue.of(instance.eventId) ?: return@forEach
      val key = keyOf(entry, instance) ?: return@forEach
      val area = catalogue.eventOf(entry).heldArea(instance) ?: return@forEach

      masters.filter { hypot(it.x - area.x, it.y - area.y) <= area.radius }.forEach { master ->
        ledger.add(key, master.masterId, master.name, ContributionLedger.Axis.HOLDING, HOLDING_PER_RUN)
      }
    }
  }

  /** What an event's creatures carry: its kind and its subject. Null for an event about nothing in particular. */
  private fun keyOf(entry: WorldEventEntry, instance: WorldEventInstance): ContributionLedger.Key? =
    instance.subjectId?.let { ContributionLedger.Key(catalogue.eventOf(entry).script, it) }

  private fun accrue(row: EventProvince, now: Long) {
    val days = (now - row.accruedAtSecond).coerceAtLeast(0L) / SECONDS_PER_DAY
    row.budget = (row.budget + days * config.budgetPerBestiaDay).coerceAtMost(config.budgetCap)
    row.accruedAtSecond = now
  }

  /** A row per province of the current split; rows of an older split start over. */
  private fun provinceRows(): Map<Int, EventProvince> {
    val split = provinces.provinces
    val now = clock.now().absoluteSecond
    val stored = provinceRows.findAll().associateBy { it.provinceIndex }

    val stale = stored.values.filter { it.partitionVersion != split.partitionVersion || it.provinceIndex >= split.count }
    if (stale.isNotEmpty()) provinceRows.deleteAll(stale)

    return split.provinces.associate { province ->
      val row = stored[province.index]?.takeIf { it !in stale }
        ?: provinceRows.save(EventProvince(province.index, split.partitionVersion, accruedAtSecond = now))
      province.index to row
    }
  }

  private fun pickWeighted(candidates: List<WorldEventEntry>, random: Random): WorldEventEntry {
    var roll = random.nextDouble() * candidates.sumOf { it.weight }
    for (candidate in candidates) {
      roll -= candidate.weight
      if (roll < 0.0) return candidate
    }
    return candidates.last()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val RUN_EVERY_MS = 5 * 60 * 1000L
    /** One roll per province per window of Bestia seconds, so a rerun inside the window decides the same. */
    const val ROLL_WINDOW_SECONDS = 600L
    const val SECONDS_PER_DAY = 86_400.0

    val RUNNING = listOf(State.OMEN, State.ACTIVE)

    /**
     * What holding ground for one five-minute run is worth: as much as a level-10 kill. An hour on the ground of a
     * rift weighs about as much as a dozen such kills.
     */
    const val HOLDING_PER_RUN = 10.0
  }
}
