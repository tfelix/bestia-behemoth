package net.bestia.zone.bestia.diary

import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.bestia.persistence.DiaryLine
import java.time.Duration
import java.time.Instant

/**
 * What one bestia did while its master was away, from its diary. Facts only: the game and the app put them in words.
 *
 * @property brought the goods it hauled, summed; [broughtTo] the named chests they went to.
 * @property madeAt the queues it finished, per station kind.
 * @property timeIn the seconds it spent in each state.
 * @property incidents what its master should know of, oldest first.
 * @property now what it does as the report is made: an activity kind, [NOT_IN_WORLD] or [DEAD]; [nowAt] its station.
 * @property blocker why it stopped, while it still has; [hint] says what gets it going again.
 */
data class AwayReport(
  val playerBestiaId: Long,
  val name: String?,
  val species: String,
  val brought: Map<String, Int>,
  val broughtTo: List<String>,
  val madeAt: Map<String, Int>,
  val timeIn: Map<BestiaState, Long>,
  val incidents: List<Incident>,
  val now: String,
  val nowAt: String?,
  val blocker: Blocker?,
) {

  val hint: Hint?
    get() {
      return blocker?.hint
    }

  /** Whether there is anything to tell: time alone, with nothing done or met, is not news. */
  val isEventful: Boolean
    get() {
      return brought.isNotEmpty() || madeAt.isNotEmpty() || incidents.isNotEmpty() || blocker != null
    }

  data class Incident(val kind: Kind, val at: Instant, val reason: String?, val place: String?)

  enum class Hint { EMPTY_CHEST, BUILD_SHAFT, FEED_FIRE, ADD_INGREDIENTS, BRING_FOOD, REVIVE }

  /** A stop that lasts until its master acts. Named like the diary's pause reasons, so a line names its own. */
  enum class Blocker(val hint: Hint) {
    CHEST_FULL(Hint.EMPTY_CHEST),
    NO_ORE(Hint.BUILD_SHAFT),
    STATION_OUT(Hint.FEED_FIRE),
    CANNOT_CRAFT(Hint.ADD_INGREDIENTS),
    OUT_OF_FOOD(Hint.BRING_FOOD),
    DIED(Hint.REVIVE),
  }

  /** The bestia as it is now. [state] is null while it is not in the world or dead. */
  data class Subject(
    val playerBestiaId: Long,
    val name: String?,
    val species: String,
    val now: String,
    val nowAt: String? = null,
    val state: BestiaState? = null,
  )

  companion object {
    const val NOT_IN_WORLD = "NOT_IN_WORLD"
    const val DEAD = "DEAD"

    /** The newest are kept: a guard in a busy area is attacked often, and the report stays short. */
    const val MAX_INCIDENTS = 20

    private val INCIDENT_KINDS = setOf(
      Kind.ATTACKED, Kind.RETREATED, Kind.HELPED, Kind.DIED, Kind.LOST, Kind.OUT_OF_FOOD, Kind.LOW_FOOD, Kind.WORK_PAUSED,
      Kind.FIELD_EMPTY, Kind.FALLBACK_STARTED,
    )

    /** Lines after which a state no longer counts: a new state, or the end of the bestia's day. */
    private val ENDS_A_STATE = setOf(Kind.STATE, Kind.DIED, Kind.LOST)

    /** Lines that show it went back to work after a stop. */
    private val SHOWS_WORK = setOf(Kind.HAULED, Kind.QUEUE_FINISHED)

    /** [lines] may hold the whole diary: lines before [since] still say what state it was in and why it stopped. */
    fun of(subject: Subject, lines: List<DiaryLine>, since: Instant, until: Instant): AwayReport {
      val history = lines.filter { it.playerBestiaId == subject.playerBestiaId && !it.at.isAfter(until) }.sortedBy { it.at }
      val window = history.filter { !it.at.isBefore(since) }
      val hauls = window.filter { it.kind == Kind.HAULED }

      return AwayReport(
        playerBestiaId = subject.playerBestiaId,
        name = subject.name,
        species = subject.species,
        brought = summed(hauls),
        broughtTo = hauls.mapNotNull { it.place }.distinct(),
        madeAt = window.filter { it.kind == Kind.QUEUE_FINISHED }.groupingBy { it.place ?: UNKNOWN_PLACE }.eachCount(),
        timeIn = timeIn(history, since, until, stillCounting = subject.state != null),
        incidents = window.filter { it.kind in INCIDENT_KINDS }.takeLast(MAX_INCIDENTS)
          .map { Incident(it.kind, it.at, it.reason, it.place) },
        now = subject.now,
        nowAt = subject.nowAt,
        blocker = blockerOf(subject, history),
      )
    }

    private fun summed(hauls: List<DiaryLine>): Map<String, Int> {
      val brought = LinkedHashMap<String, Int>()
      hauls.forEach { line -> line.itemAmounts().forEach { (identifier, amount) -> brought.merge(identifier, amount, Int::plus) } }
      return brought
    }

    /** Each state lasts from its line to the next; the last one up to [until] while the bestia still lives in the world. */
    private fun timeIn(history: List<DiaryLine>, since: Instant, until: Instant, stillCounting: Boolean): Map<BestiaState, Long> {
      val seconds = LinkedHashMap<BestiaState, Long>()
      var current: Pair<BestiaState, Instant>? = null

      for (line in history.filter { it.kind in ENDS_A_STATE }) {
        current?.let { (state, from) -> count(seconds, state, maxOf(from, since), minOf(line.at, until)) }
        current = stateOf(line)?.let { it to line.at }
      }
      if (stillCounting) current?.let { (state, from) -> count(seconds, state, maxOf(from, since), until) }

      return seconds
    }

    private fun count(seconds: MutableMap<BestiaState, Long>, state: BestiaState, from: Instant, to: Instant) {
      if (!to.isAfter(from)) return

      seconds.merge(state, Duration.between(from, to).seconds, Long::plus)
    }

    private fun stateOf(line: DiaryLine): BestiaState? {
      if (line.kind != Kind.STATE) return null

      return BestiaState.entries.firstOrNull { it.name == line.reason }
    }

    /** The last stop, unless the bestia worked since or works now. A dead bestia waits for its master whatever it did. */
    private fun blockerOf(subject: Subject, history: List<DiaryLine>): Blocker? {
      if (subject.now == DEAD) return Blocker.DIED
      if (subject.state == BestiaState.WORKING) return null

      val lastStop = history.indexOfLast { stopOf(it) != null }
      if (lastStop < 0) return null
      val workedSince = history.drop(lastStop + 1).any { it.kind in SHOWS_WORK || stateOf(it) == BestiaState.WORKING }

      return if (workedSince) null else stopOf(history[lastStop])
    }

    private fun stopOf(line: DiaryLine): Blocker? {
      return when (line.kind) {
        Kind.OUT_OF_FOOD -> Blocker.OUT_OF_FOOD
        Kind.WORK_PAUSED -> Blocker.entries.firstOrNull { it.name == line.reason }
        else -> null
      }
    }

    private const val UNKNOWN_PLACE = "STATION"
  }
}
