package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.Locale

/**
 * Times the same world built serially and in parallel: `./gradlew :worldgen:bench -Pcells=512`.
 *
 * Why a harness rather than two runs of the viewer with a stopwatch. Measuring this on a desktop the
 * ordinary way does not work - the first measurement taken here varied between six and eighteen seconds
 * for identical work, because a chat client and a browser were taking two cores of the twelve, and that
 * penalises the parallel build specifically while barely touching the serial one. Two numbers from two
 * separate runs an hour apart are then not comparable at all.
 *
 * Three things fix that, and all three are the point of this file:
 *
 * - **One JVM.** Both modes see the same JIT state, the same heap, the same page cache. Process startup
 *   and class loading, which are a large fraction of a short run, are paid once and measured never.
 * - **Alternating repetitions.** Serial, parallel, serial, parallel. Background load drifts over minutes,
 *   so interleaving spreads it across both modes instead of handing it all to whichever ran second.
 * - **Minimum, not mean.** Contention only ever makes a run slower, so under additive noise the fastest
 *   observation is the best estimate of the true cost. A mean measures the neighbours' CPU usage.
 *
 * The per-stage split is what makes the output actionable: a total says a change helped, and the table
 * says which stage it helped and which one is now the ceiling.
 */
object BenchMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val cells = args.valueOf("--cells")?.toInt() ?: 512
    val reps = args.valueOf("--reps")?.toInt() ?: 3
    val config = StandardWorld.demoConfig().copy(widthCells = cells, heightCells = cells)

    check(Parallel.threads > 1) { "this machine reports one core; there is nothing to compare" }
    // -Pserial reaches every worldgen task, and here it would make both arms serial and report a difference
    // of zero as though that were a finding. Refusing is the only honest answer.
    check(Parallel.enabled) { "-Pserial forces both arms onto one thread; drop it to compare them" }
    println("benchmarking ${cells}x$cells, $reps reps, ${Parallel.threads} workers")

    val serial = Run("serial")
    val parallel = Run("parallel")

    // Discarded: the first build of a JVM pays for class loading and runs interpreted until C2 catches up,
    // and on this pipeline that is worth several seconds - more than the difference being measured.
    print("  warmup... ")
    Parallel.serially { StandardWorld.build(config, StageListener.NONE) }
    StandardWorld.build(config, StageListener.NONE)
    println("done")

    Timings.drain()

    for (rep in 1..reps) {
      print("  rep $rep serial... ")
      Parallel.serially { StandardWorld.build(config, serial) }
      serial.absorb(Timings.drain())

      print("parallel... ")
      StandardWorld.build(config, parallel)
      parallel.absorb(Timings.drain())
      println("ok")
    }

    report(serial, parallel)
    if (Timings.enabled) reportSub(serial, parallel)
  }

  /** Fastest observation per stage, which is the estimate contention cannot inflate. */
  private class Run(val label: String) : StageListener {
    val best = LinkedHashMap<String, Long>()
    val sub = LinkedHashMap<String, Long>()

    override fun onStageFinish(stage: Stage, region: CellRegion, result: StageResult, millis: Long) {
      best.merge(stage.id.name, millis, ::minOf)
    }

    /** Same minimum-of-reps treatment for the sub-stage counters, which only [Timings] can see. */
    fun absorb(drained: Map<String, Long>) {
      for ((name, nanos) in drained) sub.merge(name, nanos / 1_000_000, ::minOf)
    }

    fun total() = best.values.sum()
  }

  private fun reportSub(serial: Run, parallel: Run) {
    println()
    val names = (serial.sub.keys + parallel.sub.keys)
      .sortedByDescending { maxOf(serial.sub[it] ?: 0L, parallel.sub[it] ?: 0L) }
    if (names.isEmpty()) return

    val width = names.maxOf { it.length }
    println("  %-${width}s %9s %9s %8s".format(Locale.ROOT, "sub-stage", "serial", "parallel", "speedup"))
    for (name in names) {
      val a = serial.sub[name] ?: 0L
      val b = parallel.sub[name] ?: 0L
      println(
        "  %-${width}s %7d ms %7d ms %7.2fx"
          .format(Locale.ROOT, name, a, b, if (b > 0) a.toDouble() / b else 0.0)
      )
    }
  }

  private fun report(serial: Run, parallel: Run) {
    val stages = serial.best.keys + parallel.best.keys
    val width = stages.maxOf { it.length }

    println()
    println("  %-${width}s %9s %9s %8s".format(Locale.ROOT, "stage", "serial", "parallel", "speedup"))
    for (stage in stages) {
      val a = serial.best[stage] ?: 0L
      val b = parallel.best[stage] ?: 0L
      println(
        "  %-${width}s %7d ms %7d ms %7.2fx"
          .format(Locale.ROOT, stage, a, b, if (b > 0) a.toDouble() / b else 0.0)
      )
    }

    val a = serial.total()
    val b = parallel.total()
    println("  %-${width}s %7d ms %7d ms %7.2fx".format(Locale.ROOT, "TOTAL", a, b, a.toDouble() / b))
  }

  private fun List<String>.valueOf(flag: String): String? {
    val at = indexOf(flag)
    return if (at < 0 || at == size - 1) null else this[at + 1]
  }

  private fun Array<String>.valueOf(flag: String): String? = toList().valueOf(flag)
}
