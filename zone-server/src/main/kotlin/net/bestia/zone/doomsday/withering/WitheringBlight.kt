package net.bestia.zone.doomsday.withering

import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.doomsday.persistence.StoredWitheredCell
import net.bestia.zone.doomsday.persistence.StoredWitheredCellRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * Kills the plants on land the Withering rotted deep enough: trees, shrubs, herbs and reeds, for good, whether anyone
 * is near or not. A few cells per run, because finding a cell's plants means generating its sixteen columns.
 */
@Component
class WitheringBlight(
  private val config: WitheringConfig,
  private val field: CorruptionField,
  private val withered: StoredWitheredCellRepository,
  private val worldService: WorldService,
  private val world: WorldView,
  private val residency: WorldObjectResidencyService,
  private val removal: PropRemoval,
  private val divergence: WorldObjectDivergenceRegistry,
) {

  private data class Plant(val propId: Long, val kind: StaticEntityKind)

  /** Read on first use, which is after a new world wiped the rows: a world is only ever replaced at boot. */
  private val done: MutableSet<Long> by lazy { withered.findAll().mapTo(HashSet()) { it.cellKey } }

  /** Off the tick. The plants are found here; only their removal waits for the tick. */
  fun killPlants() {
    val due = field.corrupted()
      .filter { (cell, intensity) -> intensity >= config.plantsDieFrom && cell.key !in done }
      .take(config.blightCellsPerRun)
      .map { it.first }
    if (due.isEmpty()) {
      return
    }

    val plants = due.flatMap(::plantsIn)
    world.post { plants.forEach { remove(this, it) } }
    due.forEach { done += it.key }
    withered.saveAll(due.map { StoredWitheredCell(it.key) })
  }

  private fun plantsIn(cell: ManaField.Cell): List<Plant> {
    val minX = cell.x * ManaField.CELL_METRES
    val minY = cell.y * ManaField.CELL_METRES
    val chunkMetres = worldService.config.chunkSize * worldService.config.voxelSize

    val plants = ArrayList<Plant>()
    for (chunkY in chunkOf(minY, chunkMetres)..chunkOf(minY + ManaField.CELL_METRES - 1, chunkMetres)) {
      for (chunkX in chunkOf(minX, chunkMetres)..chunkOf(minX + ManaField.CELL_METRES - 1, chunkMetres)) {
        val props = worldService.generated.propsIn(chunkX, chunkY)
        for (i in props.indices) {
          val inCell = props.xAt(i) - minX in 0.0..<ManaField.CELL_METRES &&
            props.yAt(i) - minY in 0.0..<ManaField.CELL_METRES
          if (inCell && props.kindAt(i) in PLANTS) {
            val kind = StaticEntityKind.of(props.kindAt(i), props.isBlighted(i), props.isLarge(i), props.subKindAt(i))
            plants += Plant(props.identityAt(i), kind)
          }
        }
      }
    }
    return plants
  }

  private fun remove(world: World, plant: Plant) {
    val goneForGood = divergence.of(plant.propId)?.let { it.resumeAt == null } == true
    if (goneForGood) {
      return
    }

    val chunkSize = worldService.config.chunkSize.toLong()
    val chunkX = Math.floorDiv(PropId.cellXOf(plant.propId), chunkSize).toInt()
    val chunkY = Math.floorDiv(PropId.cellYOf(plant.propId), chunkSize).toInt()
    val resident = residency.entitiesIn(chunkX, chunkY).firstOrNull { entity ->
      world.get(entity, WorldObjectIdentity::class)?.propId == plant.propId
    }

    if (resident != null) {
      removal.removed(world, resident, PropRemoval.Cause.GONE_FOR_GOOD)
    } else {
      removal.removedUnseen(plant.propId, plant.kind, PropRemoval.Cause.GONE_FOR_GOOD)
    }
  }

  private fun chunkOf(metres: Double, chunkMetres: Double): Int {
    return floor(metres / chunkMetres).toInt()
  }

  private companion object {
    val PLANTS = setOf(PropKind.TREE, PropKind.SHRUB, PropKind.HERB, PropKind.REED)
  }
}
