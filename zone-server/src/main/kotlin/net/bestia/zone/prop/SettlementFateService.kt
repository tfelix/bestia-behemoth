package net.bestia.zone.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementFates
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Decides when a settlement has fallen: when every one of its buildings is destroyed, whatever destroyed them.
 *
 * Derived from the divergence rows instead of stored beside them, so the two cannot disagree: at boot from every
 * destroyed building on record, and live from each depletion. Tick thread only, like the site index it reads.
 */
@Service
class SettlementFateService(
  private val divergence: WorldObjectDivergenceRegistry,
  private val sites: SettlementSiteIndex,
  private val residency: WorldObjectResidencyService,
  private val worldService: WorldService,
) : SettlementFates {

  private val fallen = HashSet<Int>()
  private val listeners = ArrayList<(Int) -> Unit>()

  init {
    divergence.onDepleted(::onDepleted)
  }

  override fun hasFallen(settlement: Int): Boolean {
    return settlement in fallen
  }

  override fun fallen(): Set<Int> {
    return fallen
  }

  override fun onFell(listener: (settlement: Int) -> Unit) {
    listeners.add(listener)
  }

  /** Finds the settlements that fell before this run. At boot, after the divergence rows are loaded. */
  fun deriveAll() {
    val touched = divergence.all()
      .filter { (_, entry) -> isDestroyedBuilding(entry) }
      .mapNotNull { (propId, _) -> settlementOf(propId) }
      .toSet()

    fallen.addAll(touched.filter(::isRazed))
    LOG.info { "${fallen.size} of ${touched.size} damaged settlements have fallen" }
  }

  /**
   * Destroys every building of [settlement] that is still standing, as if each had been knocked down. For GM
   * testing; on the tick.
   *
   * @return how many buildings it destroyed
   */
  fun raze(world: World, settlement: Int): Int {
    val site = sites.siteOf(settlement) ?: return 0
    var razed = 0

    for (building in propsOf(site)) {
      if (isDestroyed(building.propId)) continue

      // Removed first: the entity's own pose is what locates it, and a resident prop with a divergence row
      // would otherwise linger until its column next unloads.
      residentEntityOf(world, building.propId)?.let { residency.remove(world, it) }
      val kind = StaticEntityKind.of(PropKind.BUILDING, blighted = false, large = false, building.function.ordinal)
      divergence.recordDepletion(building.propId, kind, resumeAt = null)
      razed++
    }

    return razed
  }

  private fun onDepleted(propId: Long, entry: DivergenceEntry) {
    if (!isDestroyedBuilding(entry)) return

    val settlement = settlementOf(propId) ?: return
    if (settlement in fallen || !isRazed(settlement)) return

    fallen.add(settlement)
    LOG.info { "Settlement $settlement has fallen: its last building was destroyed" }
    for (listener in listeners) listener(settlement)
  }

  private fun isDestroyedBuilding(entry: DivergenceEntry): Boolean {
    return entry.kind.isBuilding && entry.resumeAt == null
  }

  private fun isDestroyed(propId: Long): Boolean {
    val entry = divergence.of(propId) ?: return false
    return entry.resumeAt == null
  }

  /** The settlement whose own building [propId] names, found from where the building stands. */
  private fun settlementOf(propId: Long): Int? {
    val voxelSize = worldService.config.voxelSize
    val x = Math.floor((PropId.cellXOf(propId) + 0.5) / voxelSize).toLong()
    val y = Math.floor((PropId.cellYOf(propId) + 0.5) / voxelSize).toLong()
    val site = sites.siteCovering(x, y) ?: return null

    return site.index.takeIf { site.buildingOf(propId) != null }
  }

  /** Whether every building of [settlement] that is a prop is destroyed. A fortification is voxels, never a prop. */
  private fun isRazed(settlement: Int): Boolean {
    val site = sites.siteOf(settlement) ?: return false
    val buildings = propsOf(site)

    return buildings.isNotEmpty() && buildings.all { isDestroyed(it.propId) }
  }

  private fun propsOf(site: SettlementSite): List<SettlementSite.Building> {
    return site.buildings.filter { it.function != BuildingFunction.FORTIFICATION }
  }

  private fun residentEntityOf(world: World, propId: Long): Long? {
    val chunkSize = worldService.config.chunkSize.toLong()
    val chunkX = Math.floorDiv(PropId.cellXOf(propId), chunkSize).toInt()
    val chunkY = Math.floorDiv(PropId.cellYOf(propId), chunkSize).toInt()

    return residency.entitiesIn(chunkX, chunkY).firstOrNull { entity ->
      world.get(entity, WorldObjectIdentity::class)?.propId == propId
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
