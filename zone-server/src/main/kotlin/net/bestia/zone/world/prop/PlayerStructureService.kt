package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.construction.ConstructionSite
import net.bestia.zone.ecs.movement.Position
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
  private val sites: ConstructionSiteSpawner,
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

  /**
   * The nearest site of [kind] being built within [RANGE_TILES] of [around], or null.
   *
   * The counterpart to [stationNear], which cannot see one: a site is an ordinary entity in the `DYNAMIC`
   * layer with no `StaticVisual`, so without this a player could stack a second workbench on top of the one
   * they are halfway through building.
   */
  fun siteNear(world: World, around: Vec3L, kind: StaticEntityKind): EntityId? {
    return aoi.queryEntitiesInCube(around, RANGE_TILES * 2, setOf(AoiLayer.DYNAMIC))
      .firstOrNull { world.get(it, ConstructionSite::class)?.kind == kind }
  }

  /**
   * Starts a structure rather than finishing one: records the row unbuilt and puts a site into the world.
   *
   * The mirror of [place] for anything that has to be *worked on* - a kit used out of the inventory - where
   * that one is for a skill that raises a station outright. Both refuse ground that already holds one of the
   * same kind, and this also refuses ground that holds a half-built one.
   *
   * Unlike [place] there is no residency call: a site is an ordinary entity, so nothing has to be waiting on
   * that column for it to appear.
   *
   * @return the new site's entity id, or null when the ground is taken
   */
  fun beginConstruction(
    world: World,
    kind: StaticEntityKind,
    ownerMasterId: Long,
    position: Vec3L,
    yaw: Float,
    buildSeconds: Float
  ): EntityId? {
    if (stationNear(world, position, kind) != null || siteNear(world, position, kind) != null) {
      LOG.debug { "Master $ownerMasterId tried to start a $kind next to one at $position" }
      return null
    }

    val chunkSize = worldService.config.chunkSize.toLong()
    val entry = structures.place(
      kind = kind,
      ownerMasterId = ownerMasterId,
      position = position,
      yaw = yaw,
      chunkX = Math.floorDiv(position.x, chunkSize).toInt(),
      chunkY = Math.floorDiv(position.y, chunkSize).toInt(),
      buildSeconds = buildSeconds
    )

    val entityId = sites.spawn(world, entry)

    LOG.info { "Master $ownerMasterId started a $kind at $position (structure ${entry.id})" }

    return entityId
  }

  /**
   * Turns a finished site into the structure it was going to be: the row stops being a site, the site entity
   * is destroyed, and the real static prop goes up in its place.
   *
   * The swap is deliberate rather than a flag flip. A standing structure belongs on the per-chunk static
   * channel with every other prop - it never changes again - and only a site needs to be an entity.
   *
   * Called from inside a system, where both halves are already safe to interleave: `World.destroy` is
   * deferred to the end of the tick, and `placeNow` queues its column batch for the same reason.
   */
  fun completeConstruction(world: World, siteEntityId: EntityId, site: ConstructionSite) {
    // Where it actually stands rather than where it was placed - see `PlayerStructureRegistry.finish`.
    val settled = world.get(siteEntityId, Position::class)?.toVec3L()
    if (settled == null) {
      LOG.warn { "Construction site $siteEntityId has no position and cannot be completed" }
      return
    }

    structures.finish(site.structureId, settled)

    val entry = structures.of(site.structureId)
    if (entry == null) {
      LOG.warn { "Structure ${site.structureId} vanished while its site was being completed" }
      return
    }

    world.destroy(siteEntityId)
    residency.placeNow(world, source.siteOf(entry))

    LOG.info { "Master ${site.ownerMasterId} finished a ${site.kind} at $settled (structure ${entry.id})" }
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
