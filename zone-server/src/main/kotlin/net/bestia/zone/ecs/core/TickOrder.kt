package net.bestia.zone.ecs.core

/**
 * Resolves the order systems run in: by [Phase], then inside a phase by [System.after] and [System.before], ties
 * broken by name.
 *
 * It refuses an order it would have to guess. Two systems of one phase that touch the same component must be
 * ordered by [System.after] or [System.before], because the scheduler sees nothing else, and nothing may run
 * after a system of a later phase. Instances of one class keep the order they were registered in.
 */
object TickOrder {

  fun of(systems: List<System>): List<System> {
    val problems = mutableListOf<String>()
    problems += afterAcrossPhases(systems)

    val ordered = Phase.entries.flatMap { phase ->
      orderWithin(systems.filter { it.phase == phase }, problems)
    }

    check(problems.isEmpty()) { "The tick order is ambiguous:\n" + problems.joinToString("\n") { " - $it" } }

    return ordered
  }

  /** Two systems conflict when one writes a component type the other reads or writes. */
  fun conflicts(a: System, b: System): Boolean {
    return a.writes.any { it in b.reads || it in b.writes } || b.writes.any { it in a.reads || it in a.writes }
  }

  private fun orderWithin(systems: List<System>, problems: MutableList<String>): List<System> {
    val remaining = systems.toMutableList()
    val placed = ArrayList<System>(systems.size)

    while (remaining.isNotEmpty()) {
      val next = remaining
        .filter { candidate -> remaining.none { other -> other !== candidate && runsAfter(candidate, other) } }
        .minByOrNull { it.name }

      if (next == null) {
        problems += "${remaining.joinToString { it.name }} wait for each other through `after`"
        return placed + remaining
      }

      placed += next
      remaining -= next
    }

    problems += unordered(placed)

    return placed
  }

  private fun unordered(placed: List<System>): List<String> {
    val problems = mutableListOf<String>()

    for (i in placed.indices) {
      for (j in i + 1 until placed.size) {
        val earlier = placed[i]
        val later = placed[j]
        if (earlier::class == later::class || !conflicts(earlier, later)) continue
        if (later.isTransitivelyAfter(earlier, placed)) continue

        problems += "${earlier.name} and ${later.name} both touch ${shared(earlier, later)} in ${later.phase}; " +
          "declare which runs first with `after`"
      }
    }

    return problems
  }

  private fun afterAcrossPhases(systems: List<System>): List<String> {
    return systems.flatMap { system ->
      systems
        .filter { other -> other.phase > system.phase && runsAfter(system, other) }
        .map { other -> "${system.name} (${system.phase}) runs after ${other.name}, which is in the later ${other.phase}" }
    }
  }

  /** Whether [system] has to run after [other], whichever of the two declared it. */
  fun runsAfter(system: System, other: System): Boolean {
    return system.after.any { it.isInstance(other) } || other.before.any { it.isInstance(system) }
  }

  private fun System.isTransitivelyAfter(earlier: System, phase: List<System>): Boolean {
    val seen = HashSet<System>()
    val pending = ArrayDeque(listOf(this))

    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      for (before in phase.filter { runsAfter(current, it) }) {
        if (before === earlier) return true
        if (seen.add(before)) pending.addLast(before)
      }
    }

    return false
  }

  private fun shared(a: System, b: System): String {
    val touched = (a.writes intersect (b.reads + b.writes)) + (b.writes intersect (a.reads + a.writes))
    return touched.mapNotNull { it.simpleName }.sorted().joinToString()
  }
}
