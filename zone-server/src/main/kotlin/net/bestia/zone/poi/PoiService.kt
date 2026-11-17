package net.bestia.zone.poi

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.poi.PoiChannels
import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.poi.PoiStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.Poi
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.poi.persistence.DestroyedPoi
import net.bestia.zone.poi.persistence.DestroyedPoiRepository
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * The POIs the world generator placed, as entities.
 *
 * Raised from the world's markers on every boot, so stats, effects and scripts always follow the current
 * `prop-kinds.yml`. Only destruction is stored: a destroyed POI stays gone for the rest of the world.
 *
 * Tick thread only after boot.
 */
@Service
class PoiService(
  private val worldService: WorldService,
  private val kinds: PropKindRegistry,
  private val catalog: PoiCatalog,
  private val statusEffectService: StatusEffectService,
  private val repository: DestroyedPoiRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  internal data class Site(val kind: PoiKind, val instanceId: Int, val position: Vec3L)

  private val destroyed = HashSet<DestroyedPoi.Key>()

  /** Boot only. Raises every POI of this world that players have not destroyed. */
  fun raise(world: World) {
    loadDestroyed()

    val generated = worldService.generated
    val sites = sitesOf(generated)
    val standing = sites.filterNot { keyOf(it.kind, it.instanceId) in destroyed }
    standing.forEach { raise(world, it) }

    logCensus(generated.config.seed, sites)
    LOG.info { "POIs: ${sites.size} placed, ${sites.size - standing.size} destroyed, ${standing.size} raised" }
  }

  /** The POI's HP reached zero. It is never raised again on this world. */
  fun destroyed(world: World, poiId: EntityId) {
    val poi = world.get(poiId, Poi::class) ?: return

    val key = keyOf(poi.kind, poi.instanceId)
    destroyed.add(key)
    LOG.info { "POI ${poi.kind} #${poi.instanceId} was destroyed" }

    val row = DestroyedPoi(
      id = key,
      worldId = worldService.record.id,
      pipelineVersion = worldService.generated.world.pipelineVersion,
      destroyedAt = Instant.now(),
    )
    asyncJobExecutor.submit(key) { repository.save(row) }
  }

  private fun raise(world: World, site: Site) {
    val config = catalog.configOf(site.kind)
    val maxHp = kinds.of(StaticEntityKind.ofPoi(site.kind)).maxHp

    val id = world.createEntity { entity ->
      add(entity, Position.fromVec3(site.position))
      add(entity, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.ofPoi(site.kind).ordinal.toLong()))
      add(entity, Health(current = maxHp, max = maxHp))
      add(entity, Level(config.level))
      add(entity, CombatStats(CombatValues.ofBase(config.level, config.attributes)))
      add(entity, ScriptComponent(config.script))
      add(entity, Poi(site.kind, site.instanceId))
    }

    // Applied in the listed order, so an `INVULNERABLE` listed first turns away a later debuff.
    world.modify(id) {
      for (effect in config.effects) {
        val applied = statusEffectService.applyEffect(
          this, id, effect.id, effect.level, durationSeconds = Double.POSITIVE_INFINITY
        )
        if (!applied) LOG.warn { "POI ${site.kind} #${site.instanceId} refused its effect ${effect.id}" }
      }
    }
  }

  private fun loadDestroyed() {
    val worldId = worldService.record.id
    val pipelineVersion = worldService.generated.world.pipelineVersion

    val (valid, stale) = repository.findAll().partition {
      it.worldId == worldId && it.pipelineVersion == pipelineVersion
    }
    valid.mapTo(destroyed) { it.id }

    // A row from another world or build names a POI that is gone or now stands elsewhere.
    if (stale.isNotEmpty()) {
      LOG.info { "Dropping ${stale.size} destroyed POI row(s) of an earlier world" }
      repository.deleteAll(stale)
    }
  }

  /** An optional roll that found no place is legal and silent in worldgen, so it is said here. */
  private fun logCensus(seed: Long, sites: List<Site>) {
    for (kind in PoiKind.entries) {
      val rolled = PoiStage.rolledInstances(seed, kind).size
      val placed = sites.count { it.kind == kind }
      if (placed < rolled) LOG.info { "POI $kind: $rolled rolled, $placed found a place" }
    }
  }

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    fun keyOf(kind: PoiKind, instanceId: Int): DestroyedPoi.Key {
      return DestroyedPoi.Key(kind.name, instanceId)
    }

    fun sitesOf(generated: GeneratedWorld): List<Site> {
      val config = generated.config

      return generated.world.features.all()
        .filter { it.kind == FeatureKind.POI }
        .filterIsInstance<PointMarker>()
        .map { marker ->
          // A first guess from the heightfield; `ChunkStreamSystem.groundNewcomers` stands it on the real column.
          val ground = generated.base.heightAt(marker.position.x, marker.position.y)

          Site(
            kind = PoiKind.entries[marker.attribute(PoiChannels.KIND).toInt()],
            instanceId = marker.attribute(PoiChannels.INSTANCE_ID).toInt(),
            position = Vec3L(
              x = (marker.position.x / config.voxelSize).toLong(),
              y = (marker.position.y / config.voxelSize).toLong(),
              z = ChunkCoords.standingZ(config, ground)
            )
          )
        }
    }
  }
}
