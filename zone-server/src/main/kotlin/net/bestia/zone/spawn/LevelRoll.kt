package net.bestia.zone.spawn

import net.bestia.zone.bestia.persistence.Bestia
import kotlin.random.Random

/**
 * Which level one wild individual of a species gets: uniform over the species' authored range, so a pack of
 * level 1-3 fieldrats is not three identical clones.
 */
object LevelRoll {

  fun roll(species: Bestia, random: Random): Int {
    if (species.levelMin == species.levelMax) {
      return species.levelMin
    }

    return random.nextInt(species.levelMin, species.levelMax + 1)
  }
}
