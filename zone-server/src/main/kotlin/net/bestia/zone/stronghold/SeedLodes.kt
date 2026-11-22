package net.bestia.zone.stronghold

import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.hypot

/** The ore deposits round an Empyrite Seed, which the generator placed next to it on purpose. */
@Component
class SeedLodes(
  private val worldService: WorldService,
) {

  /** Each deposit kind within [rangeMetres] of [seed], nearest first. */
  fun kindsNear(seed: EmpyriteSeed, rangeMetres: Double): List<ResourceType> {
    val area = Aabb(seed.x - rangeMetres, seed.y - rangeMetres, seed.x + rangeMetres, seed.y + rangeMetres)

    return worldService.generated.world.features.query(area)
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .map { ResourceType.entries[it.attribute(DepositChannels.TYPE).toInt()] to hypot(it.position.x - seed.x, it.position.y - seed.y) }
      .filter { (_, distance) -> distance <= rangeMetres }
      .sortedBy { (_, distance) -> distance }
      .map { (type, _) -> type }
      .distinct()
  }
}
