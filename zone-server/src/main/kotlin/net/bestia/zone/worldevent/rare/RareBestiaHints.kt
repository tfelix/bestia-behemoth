package net.bestia.zone.worldevent.rare

import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.WorldEventConfig
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Keeps the old talk about each rare bestia going near a piece of the land it lives on, so players learn what sky
 * to wait for before they ever see one. Each run picks another spot, so over time every town near such land hears
 * of it.
 */
@Component
class RareBestiaHints(
  private val rares: RareBestiaCatalogue,
  private val ground: RareGround,
  private val rumours: RumourService,
  private val worldService: WorldService,
  private val config: WorldEventConfig,
) {

  @Scheduled(fixedDelay = HINT_EVERY_MS, initialDelay = FIRST_HINT_MS)
  fun hint() {
    if (!config.enabled || !worldService.isLoaded) return

    val spots = ground.allSpots()
    val voxel = worldService.config.voxelSize
    rares.all().forEach { rare ->
      val spot = spots.filter { rare.livesOn(it.biome) }.randomOrNull() ?: return@forEach
      rumours.post(
        RumourKind.RARE_BESTIA_HINT, (spot.x / voxel).toLong(), (spot.y / voxel).toLong(), HINT_STRENGTH,
        mapOf("rare" to Knowledge.Slot.Token(rare.hintKey)),
      )
    }
  }

  private companion object {
    /** Four times a real day: with a lifetime of eight Bestia days, a town near such land rarely forgets. */
    const val HINT_EVERY_MS = 6 * 60 * 60 * 1000L
    const val FIRST_HINT_MS = 10 * 60 * 1000L

    const val HINT_STRENGTH = 0.6
  }
}
