package net.bestia.zone.item.profession

/**
 * Which two sub-trees are a master's professions, from the points spent in each.
 *
 * A sub-tree counts from its first point. A third one replaces a held profession only when it has *more* points
 * than it; on a tie the master keeps what they hold, so two equal sub-trees never decide the gear by chance.
 * That is why the profession held so far is an input and not something to recompute from points alone.
 */
object ProfessionResolver {

  const val MAX_PROFESSIONS = 2

  fun resolve(pointsBySubTree: Map<String, Int>, held: List<Profession>): List<Profession> {
    fun pointsOf(profession: Profession): Int = pointsBySubTree[profession.subTree] ?: 0

    val kept = held.filter { pointsOf(it) > 0 }.distinct().toMutableList()
    val candidates = Profession.entries.filter { pointsOf(it) > 0 }.sortedByDescending { pointsOf(it) }

    for (candidate in candidates) {
      if (candidate in kept) continue

      if (kept.size < MAX_PROFESSIONS) {
        kept += candidate
        continue
      }

      val weakest = kept.minBy { pointsOf(it) }
      if (pointsOf(candidate) > pointsOf(weakest)) {
        kept -= weakest
        kept += candidate
      }
    }

    return kept.sortedByDescending { pointsOf(it) }
  }
}
