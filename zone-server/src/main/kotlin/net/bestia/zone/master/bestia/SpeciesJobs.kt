package net.bestia.zone.master.bestia

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.bestia.persistence.Bestia

/**
 * Which jobs a species may take under orders: a bird can gather herbs but cannot swing a pick. The list is authored
 * per mob as `jobs:` and read from [Bestia.jobs].
 */
object SpeciesJobs {

  /** What a species whose mob YML names no jobs may still do. Every species can follow its master and rest. */
  val UNTRAINED: Set<BestiaJob> = setOf(BestiaJob.REST, BestiaJob.FOLLOW)

  fun allowedFor(species: Bestia): Set<BestiaJob> {
    val named = species.jobs.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty)?.let(BestiaJob::fromNameOrNull) }

    return if (named.isEmpty()) UNTRAINED else named.toSet()
  }

  /** Whether a bestia of [species] can follow [orders]: its main job and its fallback are both among its jobs. */
  fun fits(species: Bestia, orders: AiConfig): Boolean {
    val allowed = allowedFor(species)

    return orders.job in allowed && (orders.fallbackJob == null || orders.fallbackJob in allowed)
  }

  /**
   * [orders], or Rest in their place when the species cannot take their job, which only stored orders from before
   * the species' list changed can ask. Courage and care stay as they were.
   */
  fun fitted(species: Bestia, orders: AiConfig): AiConfig {
    return if (fits(species, orders)) orders else orders.copy(job = BestiaJob.REST, fallbackJob = null)
  }
}
