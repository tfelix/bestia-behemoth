package net.bestia.zone.spawn.ecs

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.spawn.ecs.WildSpawnerService.Candidate
import kotlin.math.abs

/**
 * Picks the species for a den at a history site: the candidate nearest the level of the wild around the site.
 * The wild is the closest ordinary den, so a guard or a haunt is as strong as the country it stands in.
 */
internal class SiteSpecies(
  private val candidates: List<Candidate>,
  private val wildDens: List<PointMarker>,
) {

  /** Null when no candidate comes within [MAX_LEVEL_MISS] of the wild: a site may stay empty. */
  fun at(site: Vec2d): Candidate? {
    val wild = wildDens.minByOrNull { it.position.distanceTo(site) } ?: return null
    val level = wild.attribute(SpawnerChannels.LEVEL_MAX).toInt()
    val biome = Biome.entries[wild.attribute(SpawnerChannels.BIOME).toInt()]

    val best = candidates.minByOrNull { scoreOf(it, level, biome) } ?: return null
    return best.takeIf { abs(it.species.level - level) <= MAX_LEVEL_MISS }
  }

  private fun scoreOf(candidate: Candidate, level: Int, biome: Biome): Int {
    val homeless = candidate.habitat.isNotEmpty() && biome !in candidate.habitat
    return abs(candidate.species.level - level) + if (homeless) HABITAT_PENALTY else 0
  }

  companion object {
    /** Beyond this many levels from the wild around it, a creature would be out of place. */
    const val MAX_LEVEL_MISS = 10

    /** Counted like levels: a species of another country is chosen only when it fits the level this much better. */
    private const val HABITAT_PENALTY = 5
  }
}
