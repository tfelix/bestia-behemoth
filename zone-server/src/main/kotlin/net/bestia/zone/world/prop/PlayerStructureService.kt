package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Puts a crafting station up, and answers whether one is standing near enough to work at.
 *
 * Both halves live here rather than in [net.bestia.zone.crafting.CraftingService] because both are questions
 * about the world rather than about a recipe, and because the same proximity rule decides them: a station you
 * may not build a second one next to is a station you may craft at.
 *
 * Tick thread only - every method touches the ECS or the interest index.
 */
@Service
class PlayerStructureService(
  private val structures: PlayerStructureRegistry,
  private val residency: WorldObjectResidencyService,
  private val source: PlayerStructureSource,
  private val aoi: EntityAOIService,
  private val worldService: WorldService
) {

  /**
   * The nearest station of [kind] within [RANGE_TILES] of [around], or null.
   *
   * Goes through the interest index rather than the structure registry, and that is the point: a station is
   * only usable if it *exists in the world*, and a station whose column is not resident does not. Asking the
   * registry would let a player craft at a forge that nothing has materialised, in a chunk nobody is holding.
   */
  fun stationNear(world: World, around: Vec3L, kind: StaticEntityKind): EntityId? =
    aoi.queryEntitiesInCube(around, RANGE_TILES * 2, setOf(AoiLayer.STATIC))
      .firstOrNull { world.get(it, StaticVisual::class)?.kind == kind }

  /**
   * Records a station at [position] and brings it into the world if anyone is holding that ground.
   *
   * Writes the row first and materialises second, which is the order that cannot lose a station: a crash
   * between the two leaves a structure that appears the next time somebody walks up to it, where the reverse
   * would leave one standing that no restart brings back.
   *
   * @return null when the ground is already occupied by a station of the same kind
   */
  fun place(
    world: World,
    kind: StaticEntityKind,
    ownerMasterId: Long,
    position: Vec3L,
    yaw: Float
  ): StructureEntry? {
    if (stationNear(world, position, kind) != null) {
      LOG.debug { "Master $ownerMasterId tried to place a second $kind next to one at $position" }
      return null
    }

    val chunkSize = worldService.config.chunkSize.toLong()
    val entry = structures.place(
      kind = kind,
      ownerMasterId = ownerMasterId,
      position = position,
      yaw = yaw,
      chunkX = Math.floorDiv(position.x, chunkSize).toInt(),
      chunkY = Math.floorDiv(position.y, chunkSize).toInt()
    )

    // Null when nobody holds the column, which needs nothing: the row is written, and `PlayerStructureSource`
    // will produce it the next time that ground materialises.
    residency.placeNow(world, source.siteOf(entry))

    LOG.info { "Master $ownerMasterId placed a $kind at $position (structure ${entry.id})" }

    return entry
  }

  companion object {
    /**
     * How far a crafter may stand from their station, in tiles, and how close two of a kind may be built.
     *
     * Matches the `range: 2` the crafting skills carry in `skills.yml`, which is what the client uses to decide
     * whether the cursor is close enough to activate at all.
     */
    const val RANGE_TILES = 2L

    private val LOG = KotlinLogging.logger { }
  }
}
