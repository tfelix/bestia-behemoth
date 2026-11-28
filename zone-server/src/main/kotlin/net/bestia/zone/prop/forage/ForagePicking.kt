package net.bestia.zone.prop.forage

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.HerbPicking
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/** What picking one forage patch yields: its biome's table, cut to what the picker's Herbalism reaches. */
@Component
class ForagePicking(
  private val table: ForageTable,
  private val biomes: ForageBiome,
  private val herbs: HerbPicking,
  private val random: Random = ThreadLocalRandomSource,
) {

  /**
   * Item id to amount for a patch at [position], or null when its biome has no table - then the patch yields what
   * its `prop-kinds.yml` row says. Empty when the biome grows nothing the picker may take yet.
   */
  fun pick(world: World, pickerId: EntityId, position: Vec3L): Map<Long, Int>? {
    val biome = biomes.biomeAt(position) ?: return null
    if (table.entriesOf(biome).isEmpty()) return null

    return table.pick(biome, herbs.maxLevelFor(world, pickerId), random)
  }
}
