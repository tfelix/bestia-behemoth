package net.bestia.zone.worldevent

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaField.Companion.TILE_CELLS
import net.bestia.zone.world.mana.ManaGround
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers
import org.springframework.stereotype.Component

/** Takes the [ProvinceSignals] of every province at once. Off the tick. */
@Component
class ProvinceSignalSnapshot(
  private val provinces: ProvinceService,
  private val field: ManaField,
  private val ground: ManaGround,
  private val tiers: ManaTiers,
  private val world: WorldView,
) {

  fun take(): List<ProvinceSignals> {
    val mana = manaByProvince()
    val players = playersByProvince()

    return provinces.provinces.provinces.map { province ->
      val seen = mana[province.index]
      ProvinceSignals(
        province = province,
        isSanctuary = provinces.isSanctuary(province),
        peakCell = seen?.peakCell,
        peakDensity = seen?.peakDensity ?: 0.0,
        cellsAtTier = seen?.cellsAtTier.orEmpty(),
        playersPresent = players[province.index] ?: 0,
      )
    }
  }

  /** The province at a world position in metres, or null for open sea. */
  fun provinceIndexAt(x: Double, y: Double): Int? = provinces.provinceAt(x, y)?.index

  private class Mana {
    var peakCell: Cell? = null
    var peakDensity = 0.0
    val cellsAtTier = HashMap<ManaTier, Int>()
  }

  private fun manaByProvince(): Map<Int, Mana> = synchronized(field) {
    val byProvince = HashMap<Int, Mana>()

    field.tileKeys().forEach { key ->
      val tile = field.tileArray(key) ?: return@forEach
      val originX = ManaField.tileXOf(key) * TILE_CELLS
      val originY = ManaField.tileYOf(key) * TILE_CELLS

      tile.forEachIndexed { index, delta ->
        if (delta == 0.0) return@forEachIndexed
        val cell = Cell(originX + index % TILE_CELLS, originY + index / TILE_CELLS)
        val province = provinces.provinceAt(cell.centreX, cell.centreY) ?: return@forEachIndexed
        val density = ground.startDensity(cell) + delta

        val mana = byProvince.getOrPut(province.index) { Mana() }
        if (density > mana.peakDensity) {
          mana.peakDensity = density
          mana.peakCell = cell
        }
        val tier = tiers.tierOf(cell)
        if (tier > ManaTier.CALM) mana.cellsAtTier.merge(tier, 1, Int::plus)
      }
    }

    byProvince
  }

  private fun playersByProvince(): Map<Int, Int> {
    val positions = world.read {
      val found = ArrayList<Pair<Long, Long>>()
      query(Master::class, Position::class).each { _ -> found += get<Position>().x to get<Position>().y }
      found
    }
    // Position units are metres, see the mana field.
    return positions.mapNotNull { (x, y) -> provinces.provinceAt(x.toDouble(), y.toDouble())?.index }
      .groupingBy { it }
      .eachCount()
  }
}
