package net.bestia.zone.spawn.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.spawn.ecs.WildSpawnerService.Candidate
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * The dead that rise on a battlefield at night: one night-only den per field, at the level of the wild around it.
 * The battlefield species rise nowhere else; the undead, the demons and the ghosts of the wild join them.
 */
@Service
class BattlefieldHaunts(
  private val worldService: WorldService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val cellIndex: SpawnerCellIndex,
) {

  data class Haunt(
    val identity: DenIdentity,
    val bestiaId: Long,
    val position: Vec3L,
    val pack: Int,
    val range: Int,
  )

  /** Boot only. */
  fun placeDens(world: World) {
    val haunts = hauntsOf(worldService.generated, worldService.record.id, bestiaCatalogue.all())

    for (haunt in haunts) {
      val den = world.createEntity { id ->
        add(
          id,
          Spawner(
            identity = haunt.identity,
            bestiaId = haunt.bestiaId,
            maxSpawnCount = haunt.pack,
            position = haunt.position,
            range = haunt.range,
            nightOnly = true,
          )
        )
      }
      cellIndex.add(den, haunt.position)
    }

    LOG.info { "Placed ${haunts.size} night den(s) on battlefields" }
  }

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    private const val MIN_PACK = 2
    private const val MAX_PACK = 6

    /** One more in the pack for this many dead. */
    private const val DEAD_PER_RISEN = 500

    internal fun hauntsOf(generated: GeneratedWorld, worldId: Long, catalogue: List<Bestia>): List<Haunt> {
      val config = generated.config
      val species = SiteSpecies(
        catalogue.filter(::haunts).map(::Candidate),
        generated.world.features.all().filter { it.kind == FeatureKind.BESTIA_SPAWN }.filterIsInstance<PointMarker>()
      )

      return generated.world.features.all()
        .filter { it.kind == FeatureKind.BATTLEFIELD }
        .filterIsInstance<PointMarker>()
        .mapNotNull { field ->
          val risen = species.at(field.position) ?: return@mapNotNull null
          val ground = generated.base.heightAt(field.position.x, field.position.y)
          val fallen = field.attribute(SiteChannels.FALLEN).toInt()
          Haunt(
            identity = DenIdentity(field.id.value, worldId, generated.world.pipelineVersion),
            bestiaId = risen.species.id,
            position = Vec3L(
              x = (field.position.x / config.voxelSize).toLong(),
              y = (field.position.y / config.voxelSize).toLong(),
              z = ChunkCoords.standingZ(config, ground)
            ),
            pack = (MIN_PACK + fallen / DEAD_PER_RISEN).coerceAtMost(MAX_PACK),
            range = field.attribute(SiteChannels.RADIUS).roundToInt().coerceIn(1, Spawner.DEFAULT_ACTIVATION_RANGE),
          )
        }
    }

    /** A boss keeps to its own den: the rank and file haunt a field. */
    private fun haunts(species: Bestia): Boolean {
      if (species.eventOnly || species.worldBoss || species.boss) return false
      return species.battlefield ||
          species.kind == BestiaKind.UNDEAD ||
          species.kind == BestiaKind.DEMON ||
          species.element.base == Element.GHOST
    }
  }
}
