package net.bestia.zone.worldboss

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.coast.CoastChannels
import net.bestia.worldgen.coast.ShoreKind
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.Spawner
import net.bestia.zone.spawn.ecs.SpawnerCellIndex
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.worldboss.persistence.SlainWorldBoss
import net.bestia.zone.worldboss.persistence.SlainWorldBossRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * The world bosses that live in a lair today: one den each, holding the boss alone.
 *
 * A lair boss that players kill or catch is gone for the rest of the world. Any other death, like a fall or the
 * cold, lets the den bring it back after [RESPAWN_SECONDS].
 *
 * Tick thread only after boot, like `WorldObjectDivergenceRegistry`.
 */
@Service
class WorldBossLairService(
  private val worldService: WorldService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val cellIndex: SpawnerCellIndex,
  private val repository: SlainWorldBossRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  data class Lair(
    val identity: DenIdentity,
    val bestiaId: Long,
    /** Index into `Chronicle.monsters`. */
    val monster: Int,
    val position: Vec3L,
  )

  val lairs: List<Lair> by lazy { resolve() }

  private val denByLair = HashMap<Long, EntityId>()

  private val slain = HashSet<Long>()

  fun isLair(featureId: Long): Boolean = featureId in denByLair

  /** Boot only. Places a dormant den at every lair whose boss players have not killed. */
  fun placeDens(world: World) {
    loadSlain()

    for (lair in lairs) {
      if (lair.identity.featureId in slain) continue

      val den = world.createEntity { id ->
        add(
          id,
          Spawner(
            identity = lair.identity,
            bestiaId = lair.bestiaId,
            maxSpawnCount = 1,
            position = lair.position,
            range = 1,
            respawnSeconds = RESPAWN_SECONDS,
          )
        )
      }
      cellIndex.add(den, lair.position)
      denByLair[lair.identity.featureId] = den
    }

    LOG.info { "World bosses: ${lairs.size} lair(s), ${slain.size} slain by players, ${denByLair.size} den(s) placed" }
  }

  /** Players killed or caught the boss of this lair. Its den goes away, now and on every later boot. */
  fun defeatedByPlayers(world: World, lairFeatureId: Long, slayer: Long) {
    val den = denByLair.remove(lairFeatureId) ?: return
    val lair = lairs.first { it.identity.featureId == lairFeatureId }

    world.destroy(den)
    slain.add(lairFeatureId)
    LOG.info { "World boss ${lair.bestiaId} of lair $lairFeatureId defeated by account $slayer" }

    val row = SlainWorldBoss(
      lairFeatureId = lairFeatureId,
      worldId = lair.identity.worldId,
      pipelineVersion = lair.identity.worldVersion,
      bestiaId = lair.bestiaId,
      slainBy = slayer,
      slainAt = Instant.now(),
    )
    asyncJobExecutor.submit(lairFeatureId) { repository.save(row) }
  }

  private fun loadSlain() {
    val worldId = worldService.record.id
    val pipelineVersion = worldService.generated.world.pipelineVersion

    val (valid, stale) = repository.findAll().partition {
      it.worldId == worldId && it.pipelineVersion == pipelineVersion
    }
    valid.mapTo(slain) { it.lairFeatureId }

    // A row from another world or build names a lair that is gone or is now a different one.
    if (stale.isNotEmpty()) {
      LOG.info { "Dropping ${stale.size} slain world boss row(s) of an earlier world" }
      repository.deleteAll(stale)
    }
  }

  private fun resolve(): List<Lair> {
    val byIdentifier = bestiaCatalogue.all().associate { it.identifier to it.id }
    return lairsOf(worldService.generated, worldService.record.id) { identifier ->
      byIdentifier[identifier] ?: run {
        LOG.warn { "No species '$identifier' for a world boss lair" }
        null
      }
    }
  }

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    val RESPAWN_SECONDS = 3600f..7200f

    internal fun lairsOf(generated: GeneratedWorld, worldId: Long, bestiaIdOf: (String) -> Long?): List<Lair> {
      val config = generated.config
      val monsters = generated.world.chronicle.monsters

      return generated.world.features.all()
        .filter { it.kind == FeatureKind.LAIR }
        .filterIsInstance<PointMarker>()
        .mapNotNull { marker ->
          val monster = monsters.getOrNull(marker.attribute(SiteChannels.MONSTER).toInt()) ?: return@mapNotNull null
          val bestiaId = bestiaIdOf(monster.boss.identifier) ?: return@mapNotNull null

          // A cave lair lies on the cave floor, every other one on the ground.
          val caveFloor = marker.attribute(SiteChannels.ELEVATION)
          val home = if (caveFloor.isNaN()) landFor(generated, marker.position) else marker.position
          val floor = if (caveFloor.isNaN()) generated.base.heightAt(home.x, home.y) else caveFloor

          Lair(
            identity = DenIdentity(marker.id.value, worldId, generated.world.pipelineVersion),
            bestiaId = bestiaId,
            monster = monster.index,
            position = Vec3L(
              x = (home.x / config.voxelSize).toLong(),
              y = (home.y / config.voxelSize).toLong(),
              z = ChunkCoords.standingZ(config, floor)
            ),
          )
        }
    }

    /**
     * A lair out at sea keeps its boss on the nearest beach: nothing in the zone swims, so a boss left at sea could
     * never be reached. Any other lair stays where it is.
     */
    private fun landFor(generated: GeneratedWorld, lair: Vec2d): Vec2d {
      if (isDry(generated, lair)) return lair

      return shoreNear(generated, lair) ?: lair.also { LOG.warn { "No dry shore near the lair at $lair" } }
    }

    /** The first dry spot behind the coast nearest to [at], a gentle shore before a cliff or a marsh. */
    internal fun shoreNear(generated: GeneratedWorld, at: Vec2d): Vec2d? {
      val nearestFirst = generated.world.features.all()
        .filter { it.kind == FeatureKind.COASTLINE }
        .filterIsInstance<MarkerFeature>()
        .map { it to it.centerline.project(at) }
        .sortedBy { (_, projection) -> projection.distance }
      val (gentle, rough) = nearestFirst.partition { (line, projection) -> shoreKindAt(line, projection.u) !in ROUGH_SHORES }

      for ((_, projection) in gentle + rough) {
        val inland = (projection.point - at).normalized()
        for (setback in SHORE_SETBACKS) {
          val spot = projection.point + inland * setback
          if (isDry(generated, spot)) return spot
        }
      }
      return null
    }

    private fun shoreKindAt(line: MarkerFeature, u: Double): ShoreKind? {
      val table = line.stations ?: return null
      return ShoreKind.entries.getOrNull(table.sample(table.channel(CoastChannels.SHORE_KIND), u).toInt())
    }

    /** Above the sea and not in a lake, the test `Invariants.checkSpawnersAreOnDryLand` makes for a den. */
    private fun isDry(generated: GeneratedWorld, at: Vec2d): Boolean {
      val config = generated.config
      if (generated.base.heightAt(at.x, at.y) <= config.seaLevel + DRY_MARGIN) return false

      val waterLevel = generated.world.layers[LayerId.WATER_LEVEL] as? FloatLayer ?: return true
      val metres = config.baseResolution.metresPerCell

      return waterLevel[(at.x / metres).toInt(), (at.y / metres).toInt()].isNaN()
    }

    private val ROUGH_SHORES = setOf(ShoreKind.SEA_CLIFF, ShoreKind.SALT_MARSH)

    /** Metres behind the waterline to try, nearest first. */
    private val SHORE_SETBACKS = listOf(4.0, 8.0, 16.0, 32.0, 64.0)

    /** Metres above the sea, so the boss does not stand in the surf. */
    private const val DRY_MARGIN = 0.5
  }
}
