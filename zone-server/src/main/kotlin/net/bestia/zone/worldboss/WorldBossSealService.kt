package net.bestia.zone.worldboss

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BuildingProps
import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** Which world boss each seal crystal holds, by the crystal's prop id. */
@Service
class WorldBossSealService(
  private val worldService: WorldService,
  private val bestiaCatalogue: BestiaCatalogue,
) {

  private val bossByCrystal: Map<Long, Long> by lazy {
    val byIdentifier = bestiaCatalogue.all().associate { it.identifier to it.id }
    bossesBySealCrystal(worldService.generated) { identifier ->
      byIdentifier[identifier] ?: run {
        LOG.warn { "No species '$identifier' for a world boss seal" }
        null
      }
    }
  }

  /** The species bound in the crystal [propId], or null for any other prop. */
  fun bossOf(propId: Long): Long? = bossByCrystal[propId]

  internal companion object {
    private val LOG = KotlinLogging.logger { }

    /** Names each crystal the way `TownStructures.sealCrystals` does. */
    internal fun bossesBySealCrystal(generated: GeneratedWorld, bestiaIdOf: (String) -> Long?): Map<Long, Long> {
      val monsters = generated.world.chronicle.monsters

      return generated.world.features.all()
        .filter { it.kind == FeatureKind.SEAL }
        .filterIsInstance<PointMarker>()
        .mapNotNull { seal ->
          val monster = monsters.getOrNull(seal.attribute(SiteChannels.MONSTER).toInt()) ?: return@mapNotNull null
          val bestiaId = bestiaIdOf(monster.boss.identifier) ?: return@mapNotNull null
          val crystal = PropId.of(
            PropKind.FIXTURE, BuildingProps.cellOf(seal.position.x), BuildingProps.cellOf(seal.position.y)
          )
          crystal to bestiaId
        }
        .toMap()
    }
  }
}
