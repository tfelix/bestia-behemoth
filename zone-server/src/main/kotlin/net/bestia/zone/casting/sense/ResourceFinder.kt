package net.bestia.zone.casting.sense

import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.HerbKind
import net.bestia.zone.bestia.WildBestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * The nearest thing of each kind Resource Sense looks for, in map metres. Ore is a worldgen deposit; herbs, timber
 * and game are land where rare herbs grow, forest, and a living wild bestia. Land is sampled on rings, so a
 * patch smaller than the ring step can be missed - an instinct, not a survey.
 */
@Component
class ResourceFinder(
  private val worldService: WorldService,
) {

  fun nearestOre(east: Double, north: Double, rangeMetres: Double): SenseMark? {
    val area = Aabb(east - rangeMetres, north - rangeMetres, east + rangeMetres, north + rangeMetres)
    val deposit = worldService.generated.world.features.query(area)
      .asSequence()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .map { it to hypot(it.position.x - east, it.position.y - north) }
      .filter { (_, distance) -> distance <= rangeMetres }
      .minByOrNull { (_, distance) -> distance }
      ?.first
      ?: return null
    val type = ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]

    return SenseMark(SenseKind.ORE, deposit.position.x, deposit.position.y, type.label)
  }

  fun nearestRareHerbs(east: Double, north: Double, rangeMetres: Double): SenseMark? {
    val surface = worldService.generated.materializer.surface
    val params = worldService.generated.params.groundCover

    return nearestOnRings(east, north, rangeMetres) { x, y ->
      // A roll of zero asks only whether the land lets a rare herb grow at all.
      val herb = HerbKind.of(surface.isBlightedAt(x, y), surface.manaAt(x, y), surface.biomeAt(x, y), 0.0, params)
      if (herb == HerbKind.WILD) null else SenseMark(SenseKind.HERBS, x, y, "${herb.name.lowercase()} herbs")
    }
  }

  fun nearestTimber(east: Double, north: Double, rangeMetres: Double): SenseMark? {
    val surface = worldService.generated.materializer.surface

    return nearestOnRings(east, north, rangeMetres) { x, y ->
      val biome = surface.biomeAt(x, y)
      if (biome.canopy < TIMBER_CANOPY) null else SenseMark(SenseKind.TIMBER, x, y, biome.label)
    }
  }

  /** Game is wherever a wild bestia lives right now, so it is read off the world rather than the generator. */
  fun nearestGame(world: World, east: Double, north: Double, rangeMetres: Double): SenseMark? {
    val voxel = worldService.config.voxelSize
    var nearest: SenseMark? = null
    var nearestDistance = rangeMetres
    world.query(EntityVisual::class, Position::class).each { id: EntityId ->
      if (!WildBestia.isWild(world, id)) return@each
      val at = get<Position>().toVec3L()
      val x = at.x * voxel
      val y = at.y * voxel
      val distance = hypot(x - east, y - north)
      if (distance <= nearestDistance) {
        nearestDistance = distance
        nearest = SenseMark(SenseKind.GAME, x, y, refId = get<EntityVisual>().id)
      }
    }

    return nearest
  }

  /** Walks rings outward from the caster, so the first match is the nearest at this sampling. */
  private fun nearestOnRings(east: Double, north: Double, rangeMetres: Double, at: (Double, Double) -> SenseMark?): SenseMark? {
    var radius = 0.0
    while (radius <= rangeMetres) {
      val directions = max(1, (2 * PI * radius / RING_STEP_METRES).toInt())
      for (step in 0 until directions) {
        val angle = 2 * PI * step / directions
        at(east + cos(angle) * radius, north + sin(angle) * radius)?.let { return it }
      }
      radius += RING_STEP_METRES
    }

    return null
  }

  private companion object {
    /** About two thousand samples for the 5 km circle: cheap for a skill cast once a minute. */
    const val RING_STEP_METRES = 200.0

    /** The forests: temperate, tropical and taiga clear it, scrub and grassland do not. */
    const val TIMBER_CANOPY = 0.5
  }
}
