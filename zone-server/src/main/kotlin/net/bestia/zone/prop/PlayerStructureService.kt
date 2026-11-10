package net.bestia.zone.prop

import java.time.Clock
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.structure.StructureCollision
import net.bestia.zone.structure.StructureRasterizer
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import net.bestia.zone.entity.StaticEntityKind

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
  private val worldService: WorldService,
  private val events: ApplicationEventPublisher,
  private val collision: StructureCollision,
  private val clock: Clock = Clock.systemUTC(),
) {

  /**
   * The nearest working station of [kind] within [RANGE_TILES] of [around], or null. A cold campfire is not one.
   *
   * Goes through the interest index rather than the structure registry, and that is the point: a station is
   * only usable if it *exists in the world*, and a station whose column is not resident does not. Asking the
   * registry would let a player craft at a forge that nothing has materialised, in a chunk nobody is holding.
   */
  fun stationNear(world: World, around: Vec3L, kind: StaticEntityKind): EntityId? =
    aoi.queryEntitiesInCube(around, RANGE_TILES * 2, setOf(AoiLayer.STATIC))
      .firstOrNull { world.get(it, StaticVisual::class)?.kind == kind && canWork(world, it) }

  private fun canWork(world: World, stationId: EntityId): Boolean {
    val structureId = world.get(stationId, PlayerStructureIdentity::class)?.structureId ?: return true

    return structures.of(structureId)?.canWorkAt(clock.instant()) != false
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
   * Starts a structure from a kit: records the row unbuilt and puts a site into the world. Refuses ground that
   * already holds one of the same kind, finished or half-built.
   *
   * There is no residency call: a site is an ordinary entity, so nothing has to be waiting on that column for it
   * to appear.
   *
   * A building piece holds its cells from the start, so nothing else is built into them while it goes up.
   *
   * @return the new site's entity id, or null when the ground is taken
   */
  fun beginConstruction(
    world: World,
    kind: StaticEntityKind,
    ownerMasterId: Long,
    ownerAccountId: AccountId,
    position: Vec3L,
    requestedYaw: Float,
    buildSeconds: Float
  ): EntityId? {
    val yaw = yawOf(kind, requestedYaw)
    val isTaken = if (collision.isPiece(kind)) {
      !collision.fits(kind, position, yaw)
    } else {
      stationNear(world, position, kind) != null || siteNear(world, position, kind) != null
    }
    if (isTaken) {
      LOG.debug { "Master $ownerMasterId tried to start a $kind where the ground is taken at $position" }
      return null
    }

    val chunkSize = worldService.config.chunkSize.toLong()
    val entry = structures.place(
      kind = kind,
      ownerMasterId = ownerMasterId,
      ownerAccountId = ownerAccountId,
      position = position,
      yaw = yaw,
      chunkX = Math.floorDiv(position.x, chunkSize).toInt(),
      chunkY = Math.floorDiv(position.y, chunkSize).toInt(),
      buildSeconds = buildSeconds
    )

    if (collision.isPiece(kind)) collision.stand(entry.id, kind, position, yaw)

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
    // Where it actually stands rather than where it was placed - see `PlayerStructureRegistry.finish`. A building
    // piece stands where it was placed and holds its cells there, though its site was grounded: an upper floor is
    // not on the ground.
    val settled = if (collision.isPiece(site.kind)) {
      structures.of(site.structureId)?.position
    } else {
      world.get(siteEntityId, Position::class)?.toVec3L()
    }
    if (settled == null) {
      LOG.warn { "Construction site $siteEntityId has no position and cannot be completed" }
      return
    }

    structures.finish(site.structureId, settled)
    if (site.kind == StaticEntityKind.CAMPFIRE) {
      structures.setBurnsOutAt(site.structureId, clock.instant() + Campfire.FIRST_BURN)
    }

    val entry = structures.of(site.structureId)
    if (entry == null) {
      LOG.warn { "Structure ${site.structureId} vanished while its site was being completed" }
      return
    }

    world.destroy(siteEntityId)
    residency.placeNow(world, source.siteOf(entry))

    LOG.info { "Master ${site.ownerMasterId} finished a ${site.kind} at $settled (structure ${entry.id})" }

    entry.ownerAccountId?.let { events.publishEvent(StructureCompletedEvent(this, it, site.ownerMasterId, site.kind.name)) }
  }

  /** A building piece turns in quarter turns only, and is stored that way so it is drawn the way it blocks. */
  private fun yawOf(kind: StaticEntityKind, requested: Float): Float {
    if (!collision.isPiece(kind)) return requested

    return StructureRasterizer.snappedYaw(requested)
  }

  companion object {
    /**
     * How far a crafter may stand from their station, in tiles, and how close two of a kind may be built.
     *
     * One more than the `range: 2` the crafting skills carry in `skills.yml`, so a crafter who could aim a skill
     * at their station is also close enough to work at it.
     */
    const val RANGE_TILES = 3L

    private val LOG = KotlinLogging.logger { }
  }
}
