package net.bestia.zone.prop.omen

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTierListener
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/**
 * Raises a scar in some of the cells where the mana turns Ominous: stones, glass or twisted trees, by the
 * ground. A fifth of the cells get one, picked from the cell and the world seed, so the same field raises the
 * same scars. Never on water or inside a town.
 */
@Component
class OmenRaiser(
  private val scars: OmenScars,
  private val source: OmenScarSource,
  private val residency: WorldObjectResidencyService,
  private val worldService: WorldService,
  private val chunkService: ChunkService,
  private val settlements: SettlementSiteIndex,
) : ManaTierListener {

  override fun onTiersChanged(world: World, changes: List<ManaTiers.Change>) {
    changes
      .filter { it.from < ManaTier.OMINOUS && it.to >= ManaTier.OMINOUS }
      .forEach { raiseIn(world, it.cell) }
  }

  /** Scars the cell around [x], [y] in metres whatever the share: a rift that collapsed there always marks it. */
  fun raiseAt(world: World, x: Double, y: Double) {
    raise(world, ManaField.Cell(ManaField.cellOf(x), ManaField.cellOf(y)))
  }

  private fun raiseIn(world: World, cell: ManaField.Cell) {
    if (Math.floorMod(rollOf(cell), 100L) >= SHARE_PERCENT) return

    raise(world, cell)
  }

  private fun raise(world: World, cell: ManaField.Cell) {
    if (scars.hasScar(cell.key)) return

    val scar = scarAt(cell, cell.key, rollOf(cell)) ?: return
    if (scars.raise(scar)) residency.placeNow(world, source.siteOf(scar))
  }

  private fun rollOf(cell: ManaField.Cell): Long {
    return GenRng.hash(worldService.config.seed, cell.key, SALT)
  }

  private fun scarAt(cell: ManaField.Cell, cellKey: Long, roll: Long): OmenScars.Scar? {
    val config = worldService.config
    val x = cell.centreX + offset(roll, 8)
    val y = cell.centreY + offset(roll, 24)
    val voxelX = Math.floor(x / config.voxelSize).toLong()
    val voxelY = Math.floor(y / config.voxelSize).toLong()

    val elevation = chunkService.surfaceElevationAt(voxelX, voxelY) ?: return null
    if (elevation <= config.seaLevel) return null
    if (settlements.siteCovering(voxelX, voxelY) != null) return null

    val kind = kindFor(worldService.generated.materializer.surface.biomeAt(x, y), roll) ?: return null
    val position = Vec3L(voxelX, voxelY, ChunkCoords.standingZ(config, elevation))
    val yaw = (Math.floorMod(roll ushr 40, 360L)).toFloat() * DEGREES_TO_RADIANS

    return OmenScars.Scar(cellKey, kind, position, yaw, variant = Math.floorMod(roll ushr 50, 4L).toInt())
  }

  /** Up to [MAX_OFFSET_METRES] off the cell centre, from 16 bits of the roll. */
  private fun offset(roll: Long, shift: Int): Double =
    ((roll ushr shift) and 0xFFFF).toDouble() / 0xFFFF * 2.0 * MAX_OFFSET_METRES - MAX_OFFSET_METRES

  companion object {
    private const val SALT = 0x4F4D454EL
    private const val SHARE_PERCENT = 20L
    private const val MAX_OFFSET_METRES = 48.0
    private const val DEGREES_TO_RADIANS = (Math.PI / 180.0).toFloat()

    /** The scar a kind of ground takes, or null for open water. */
    fun kindFor(biome: Biome, roll: Long): StaticEntityKind? = when (biome) {
      Biome.OCEAN, Biome.LAKE -> null

      Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.TAIGA,
      Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST ->
        if (Math.floorMod(roll, 3L) == 0L) StaticEntityKind.OMEN_FUNGUS_RING else StaticEntityKind.OMEN_TWISTED_TREE

      Biome.TUNDRA, Biome.ALPINE, Biome.ICE_SHEET, Biome.VOLCANIC_FIELD, Biome.GEOTHERMAL_BASIN ->
        StaticEntityKind.OMEN_FLOATING_STONES

      Biome.DESERT, Biome.COLD_DESERT, Biome.DRYLAND, Biome.BADLANDS, Biome.BEACH -> StaticEntityKind.OMEN_GLASS_SAND

      Biome.BOG, Biome.SWAMP, Biome.RIPARIAN -> StaticEntityKind.OMEN_CRYSTAL_BLOOM

      else -> StaticEntityKind.OMEN_STONE_RING
    }
  }
}
