package net.bestia.zone.ecs.core

import java.util.concurrent.ForkJoinPool

/**
 * Owns the registered systems, decides which are due each tick, and executes
 * them respecting both their [Schedule] and their read/write conflicts.
 *
 * Systems are grouped into ordered *waves*. Systems within a wave are mutually
 * non-conflicting and may run in parallel (when [parallel] is enabled); waves
 * run sequentially. A system is always placed in a wave strictly later than any
 * earlier-registered conflicting system, which preserves ordering for dependent
 * systems while still allowing independent ones to share a wave.
 */
class SystemScheduler(private val parallel: Boolean = false) {

  private class Entry(val system: System) {
    var tickCounter = 0

    /**
     * Progress towards the next firing. Deliberately *not* the same quantity as [sinceLastRun]: this one
     * keeps its sub-period remainder across firings so the cadence stays phase-locked to the schedule
     * rather than rounding up to a whole number of ticks each time.
     */
    var accumulator = 0f

    /** Time actually elapsed since this entry last ran, and therefore what [effectiveDelta] is taken from. */
    var sinceLastRun = 0f

    /** Real elapsed time since this entry last ran; what actually gets passed to [System.update]. */
    var effectiveDelta = 0f
  }

  private val entries = ArrayList<Entry>()
  private var waves: List<List<Entry>> = emptyList()
  private val pool: ForkJoinPool? = if (parallel) ForkJoinPool.commonPool() else null

  val systemCount: Int get() = entries.size

  /** Number of parallel waves; exposed for testing/introspection. */
  val waveCount: Int get() = waves.size

  fun register(system: System) {
    entries.add(Entry(system))
    recomputeWaves()
  }

  fun registerAll(systems: Iterable<System>) {
    systems.forEach { entries.add(Entry(it)) }
    recomputeWaves()
  }

  fun tick(world: World, deltaTime: Float) {
    // Evaluate due-ness exactly once per entry (this mutates cadence counters).
    val due = HashSet<Entry>()
    for (e in entries) {
      if (isDue(e, deltaTime)) due.add(e)
    }
    if (due.isEmpty()) return

    for (wave in waves) {
      val toRun = wave.filter { it in due }
      if (toRun.isEmpty()) continue

      if (pool == null || toRun.size == 1) {
        toRun.forEach { it.system.update(world, it.effectiveDelta) }
      } else {
        // Run this wave's systems concurrently and wait for completion before
        // advancing to the next (dependent) wave.
        pool.submit {
          toRun.parallelStream().forEach { it.system.update(world, it.effectiveDelta) }
        }.get()
      }
    }
  }

  /**
   * Determines whether [e] runs this tick. For schedules that skip ticks, [Entry.effectiveDelta] is
   * set to the real elapsed time accumulated since the entry last ran (not just this tick's
   * [deltaTime]), so systems that integrate over time (countdowns, decay, etc.) stay correct
   * regardless of how often they're scheduled to run.
   *
   * ### Why [Schedule.EverySeconds] needs two counters
   *
   * Because the cadence and the reported delta want different arithmetic, and one accumulator cannot serve
   * both. Keeping the sub-period remainder in [Entry.accumulator] is what stops the period rounding up to a
   * whole number of ticks - a 0.25 s schedule on a 0.03 s tick would otherwise fire every 0.27 s and run 8%
   * slow. But a remainder carried forward is then counted a *second* time in the next firing's delta, which
   * is why the elapsed time handed to the system lives in [Entry.sinceLastRun] and is reset outright.
   *
   * The modulo rather than a subtraction covers the overdue case. A tick longer than the whole period - a
   * lag spike, or a coarsely stepped test - leaves the accumulator still above the threshold after one
   * subtraction, and the entry would then fire on every subsequent tick forever, permanently convinced it
   * was behind. Missed periods are dropped rather than queued up to be made good on.
   */
  private fun isDue(e: Entry, deltaTime: Float): Boolean = when (val s = e.system.schedule) {
    is Schedule.EveryTick -> {
      e.effectiveDelta = deltaTime
      true
    }

    is Schedule.EveryTicks -> {
      e.tickCounter++
      e.accumulator += deltaTime
      if (e.tickCounter >= s.n) {
        e.tickCounter = 0
        e.effectiveDelta = e.accumulator
        e.accumulator = 0f
        true
      } else false
    }

    // Two counters, and a modulo rather than a subtraction. See this function's KDoc; neither is arbitrary.
    is Schedule.EverySeconds -> {
      e.accumulator += deltaTime
      e.sinceLastRun += deltaTime
      if (e.accumulator >= s.seconds) {
        e.effectiveDelta = e.sinceLastRun
        e.sinceLastRun = 0f
        e.accumulator %= s.seconds
        true
      } else false
    }
  }

  private fun recomputeWaves() {
    val waveIndexOf = HashMap<Entry, Int>()
    val result = ArrayList<MutableList<Entry>>()

    for (e in entries) {
      // Earliest wave allowed = one past the latest conflicting earlier system.
      var minWave = 0
      for ((other, w) in waveIndexOf) {
        if (conflicts(other.system, e.system)) {
          minWave = maxOf(minWave, w + 1)
        }
      }
      while (result.size <= minWave) result.add(mutableListOf())
      result[minWave].add(e)
      waveIndexOf[e] = minWave
    }

    waves = result.filter { it.isNotEmpty() }
  }

  private fun conflicts(a: System, b: System): Boolean =
    a.writes.any { it in b.reads || it in b.writes } ||
      b.writes.any { it in a.reads || it in a.writes }
}
