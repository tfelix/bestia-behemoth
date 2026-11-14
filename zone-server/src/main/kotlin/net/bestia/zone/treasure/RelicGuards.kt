package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.SiteSpecies
import net.bestia.zone.spawn.ecs.Spawner
import net.bestia.zone.spawn.ecs.SpawnerCellIndex
import net.bestia.zone.spawn.ecs.WildSpawnerService.Candidate
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * The undead that guard a tomb or a monument with an artifact in it. One den per site, at the level of the wild
 * around it. A site where no undead species fits that level stays unguarded.
 */
@Service
class RelicGuards(
  private val worldService: WorldService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val cellIndex: SpawnerCellIndex,
) {

  data class Guard(
    val identity: DenIdentity,
    val bestiaId: Long,
    val position: Vec3L,
    val pack: Int,
    val range: Int,
  )

  /** Boot only. */
  fun placeDens(world: World) {
    val generated = worldService.generated
    val undead = bestiaCatalogue.all().filter { it.kind == BestiaKind.UNDEAD && !it.eventOnly && !it.worldBoss }
    val guards = guardsOf(generated, worldService.record.id, undead)

    for (guard in guards) {
      val den = world.createEntity { id ->
        add(
          id,
          Spawner(
            identity = guard.identity,
            bestiaId = guard.bestiaId,
            maxSpawnCount = guard.pack,
            position = guard.position,
            range = guard.range,
          )
        )
      }
      cellIndex.add(den, guard.position)
    }

    LOG.info { "Placed ${guards.size} guard den(s) at tombs and monuments that hold an artifact" }
  }

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    private val GUARDED = setOf(FeatureKind.TOMB, FeatureKind.MONUMENT)

    /** A boss guards alone; anything less comes as a small band. */
    private const val BAND = 3

    internal fun guardsOf(generated: GeneratedWorld, worldId: Long, undead: List<Bestia>): List<Guard> {
      if (undead.isEmpty()) return emptyList()
      val config = generated.config
      val species = SiteSpecies(
        undead.map(::Candidate),
        generated.world.features.all().filter { it.kind == FeatureKind.BESTIA_SPAWN }.filterIsInstance<PointMarker>()
      )

      return generated.world.features.all()
        .filter { it.kind in GUARDED }
        .filterIsInstance<PointMarker>()
        .filter { it.attribute(SiteChannels.ARTIFACT) >= 0 }
        .mapNotNull { site ->
          val guard = species.at(site.position) ?: return@mapNotNull null
          val ground = generated.base.heightAt(site.position.x, site.position.y)
          Guard(
            identity = DenIdentity(site.id.value, worldId, generated.world.pipelineVersion),
            bestiaId = guard.species.id,
            position = Vec3L(
              x = (site.position.x / config.voxelSize).toLong(),
              y = (site.position.y / config.voxelSize).toLong(),
              z = ChunkCoords.standingZ(config, ground)
            ),
            pack = if (guard.species.boss) 1 else BAND,
            range = site.attribute(SiteChannels.RADIUS).roundToInt().coerceIn(1, Spawner.DEFAULT_ACTIVATION_RANGE),
          )
        }
    }
  }
}
