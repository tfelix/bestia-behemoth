package net.bestia.zone.worldevent.apex

import net.bestia.worldgen.place.Province
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.place.ecs.PlaceRegionService
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.WorldEvent
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * The mana draws an apex bestia: the strongest species of the province's own dens, a few levels above its kin and
 * touched by the mana. It comes in from the far side of the province and roams toward the thickest mana, where
 * its home is. Killing it ends the event; otherwise it leaves when the event runs out.
 */
@Component
class ApexRoamerEvent(
  private val provinces: ProvinceService,
  private val placeRegions: PlaceRegionService,
  private val dens: WildSpawnerService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val tiers: ManaTiers,
  private val chunks: ChunkService,
  private val worldService: WorldService,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val world: WorldView,
) : WorldEvent {

  override fun fits(signals: ProvinceSignals): Boolean = signals.peakCell != null && apexOf(signals.province) != null

  override fun start(signals: ProvinceSignals): WorldEvent.Started? {
    val peak = signals.peakCell ?: return null
    val species = apexOf(signals.province) ?: return null
    val (edgeX, edgeY) = farSideOf(signals.province, peak) ?: return null

    return world.read {
      val from = grounded(edgeX, edgeY) ?: return@read null
      val home = grounded(peak.centreX, peak.centreY) ?: return@read null
      val id = spawner.spawnMob(
        this, species.id, from,
        homePosition = home,
        level = species.levelMax + APEX_LEVELS,
        manaTier = tiers.tierOf(peak),
      )
      add(id, EventCreature(script, id))
      WorldEvent.Started(from, id)
    }
  }

  override fun isDefeated(instance: WorldEventInstance): Boolean {
    val id = instance.subjectId ?: return true
    return world.read { !isAlive(id) }
  }

  /** The ground around the apex, wherever it roams. */
  override fun heldArea(instance: WorldEventInstance): WorldEvent.HeldArea? {
    val id = instance.subjectId ?: return null
    val at = world.read { get(id, Position::class)?.toVec3L() } ?: return null
    return WorldEvent.HeldArea(at.x.toDouble(), at.y.toDouble(), HELD_RADIUS_METRES)
  }

  override fun end(instance: WorldEventInstance) {
    val id = instance.subjectId ?: return
    world.read {
      deletionQueue.enqueue(id)
      if (isAlive(id)) destroy(id)
    }
  }

  /** The highest species bred by the province's dens. Never a boss: an apex is a beast, not a lord. */
  private fun apexOf(province: Province): Bestia? =
    dens.dens
      .filter { provinces.provinceAt(it.position.x.toDouble(), it.position.y.toDouble())?.index == province.index }
      .mapNotNull { bestiaCatalogue.find(it.bestiaId) }
      .filterNot { it.boss || it.worldBoss || it.corruptedOnly }
      .maxByOrNull { it.levelMax }

  /** The centre of the province's land region farthest from [peak], in metres. */
  private fun farSideOf(province: Province, peak: Cell): Pair<Double, Double>? {
    val regions = placeRegions.regions.regions
    return province.regions
      .map { regions[it] }
      .filterNot { it.isWater }
      .maxByOrNull { hypot(it.centre.x - peak.centreX, it.centre.y - peak.centreY) }
      ?.let { it.centre.x to it.centre.y }
  }

  private fun grounded(x: Double, y: Double): Vec3L? {
    val config = worldService.config
    val voxelX = Math.floor(x / config.voxelSize).toLong()
    val voxelY = Math.floor(y / config.voxelSize).toLong()
    val elevation = chunks.surfaceElevationAt(voxelX, voxelY) ?: return null
    if (elevation <= config.seaLevel) return null
    return Vec3L(voxelX, voxelY, ChunkCoords.standingZ(config, elevation))
  }

  private companion object {
    /** How far above its kin's highest level the apex is born, before the mana adds its own. */
    const val APEX_LEVELS = 5

    const val HELD_RADIUS_METRES = 300.0
  }
}
