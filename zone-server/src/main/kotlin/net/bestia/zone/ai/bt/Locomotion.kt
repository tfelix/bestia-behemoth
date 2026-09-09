package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroRoute
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.util.EntityId
import kotlin.random.Random

/**
 * How the movement leaves actually move something.
 *
 * This used to greedily step onto whichever adjacent tile pointed at the target, without checking that the
 * tile was walkable - its own note said so, and named itself as the one file to change once pathfinding
 * existed. It exists now, so this is that change: every step goes through
 * [net.bestia.zone.navigation.NavigationService], which answers from the derived walkability tiles and
 * therefore from the real voxels, players' own edits included.
 *
 * ### Two tiers, chosen by distance
 *
 * [stepToward] and friends are the *local* tier: a considered path over nearby ground, falling back to a
 * single checked step when the tick's search budget is spent. [travelTo] is the *macro* tier, for a
 * destination too far away to search column by column - it plans over the world's node graph and refines one
 * leg at a time as the traveller arrives.
 *
 * A leaf that cannot move gets `false` and decides for itself what that means; nothing here fails silently by
 * leaving an entity standing still with no way to tell.
 *
 * Holds its [navigation] dependency rather than reading it off the tick context, so a movement leaf declares
 * what it needs and can be handed a fake in a test. The action template that grounds a movement action owns
 * one of these and passes it to the leaves it builds.
 */
class Locomotion(private val navigation: NavigationService) {

  fun position(world: World, entityId: EntityId): Vec3L = world.getOrThrow(entityId, Position::class).toVec3L()

  fun distanceTo(world: World, entityId: EntityId, target: Vec3L): Long = position(world, entityId).distance(target)

  fun isMoving(world: World, entityId: EntityId): Boolean = world.has(entityId, Path::class)

  /**
   * Walks towards [target] over ground that has been checked.
   *
   * A real path when the budget allows one and a single validated step otherwise, which is what keeps a busy
   * tick from stopping anybody: the difference between the two is how far ahead the NPC has committed, not
   * whether it moves.
   *
   * @return whether the entity is now moving
   */
  fun stepToward(context: BtContext, target: Vec3L): Boolean {
    if (isMoving(context.world, context.entityId)) return true

    val from = position(context.world, context.entityId)
    val path = navigation.pathTo(from, target)
      ?: navigation.stepToward(from, target)?.let { listOf(it) }
      ?: return false

    return apply(context.world, context.entityId, path)
  }

  /**
   * Wanders within [radius] of [home], one amble of at most [stepTiles] at a time.
   *
   * Picks a destination a few tiles off and paths to it, rather than one adjacent tile per call. Both are
   * random walks, but this one produces a creature that ambles somewhere and then somewhere else, instead of
   * one that jitters between neighbouring tiles - and it costs fewer searches, not more, because one path
   * lasts several tiles.
   *
   * ### Why the step length is not the radius
   *
   * It used to be: the draw was `nextLong(1, radius)`, so widening a creature's territory also widened its
   * stride. At a radius worth calling a home range that asks [NavigationService] for a local path dozens of
   * tiles long, which is past what the local tier is sized for - the budget fallback then fires on almost
   * every attempt and the creature crawls one checked tile at a time, slower and dearer than with a small
   * radius. Territory size and stride are separate questions, so they are separate parameters, and per-search
   * cost no longer scales with the leash.
   *
   * ### Why the clamp is radial
   *
   * A per-axis `coerceIn` keeps a creature inside a *square*, which at 5 tiles is invisible and at 40 reads
   * as a fenced plot. A disc is what a home range looks like, and [Vec3L.distance] is deliberately the same
   * horizontal measure `Goals.RETURN_HOME` tests - a target this accepted but that goal considered out of
   * range would put the creature in a loop. [radius] has no default so that `BestiaDomain` stays the one
   * place a territory size is decided; this package deliberately knows nothing about the bestia domain.
   *
   * The draw is capped by the radius as well, so from anywhere inside the disc at least the inward directions
   * land inside it and there is always something to pick. A creature that starts *outside* its range - one
   * that has just fled - would otherwise have no legal candidate at all and stand still, so a target that is
   * merely closer to home than it already is counts as well. `Goals.RETURN_HOME` outranks wandering and will
   * normally have walked it back first; this only keeps the leaf from being a dead end when it has not.
   */
  fun wanderStep(
    context: BtContext,
    home: Vec3L,
    radius: Long,
    stepTiles: Long = WANDER_STEP_TILES
  ): Boolean {
    if (isMoving(context.world, context.entityId)) return true

    val from = position(context.world, context.entityId)
    val reach = minOf(stepTiles, radius).coerceAtLeast(2)

    // Shuffled and then tried in order: a wander target can land in a rock face, and trying only one
    // candidate per tick makes a creature in broken country look stuck rather than idle.
    val candidates = DIRECTIONS.shuffled(Random.Default).map { direction ->
      val distance = Random.nextLong(1, reach)
      Vec3L(
        from.x + direction.x * distance,
        from.y + direction.y * distance,
        from.z
      )
    }

    for (target in candidates) {
      if (target.x == from.x && target.y == from.y) continue
      if (!keepsTerritory(target, from, home, radius)) continue
      val path = navigation.pathTo(from, target)
        ?: navigation.stepToward(from, target)?.let { listOf(it) }
        ?: continue
      return apply(context.world, context.entityId, path)
    }

    return false
  }

  /**
   * Travels to a destination too far off to path column by column.
   *
   * Plans a macro route on first call, then tops up the entity's waypoints one leg at a time as it arrives -
   * so the cost of a long journey is spread across it, and only ground somebody is actually near is ever
   * consulted. A route planned before the world changed is replanned on its own schedule rather than the
   * moment the news breaks; see [net.bestia.zone.navigation.NavigationService.shouldReplan].
   *
   * @return whether the journey is still under way
   */
  fun travelTo(context: BtContext, destination: Vec3L, profileId: String? = null): Boolean {
    val world = context.world
    val entityId = context.entityId
    val from = position(world, entityId)

    val existing = world.get(entityId, MacroRoute::class)
    val route = when {
      existing == null || existing.destination != destination ->
        navigation.planRoute(from, destination, profileId)?.also { world.add(entityId, it) }
          ?: return false

      navigation.shouldReplan(existing, context.currentTick, context.tickRate) ->
        navigation.planRoute(from, destination, profileId)?.also { world.add(entityId, it) }
          ?: existing

      else -> existing
    }

    if (route.isFinished) {
      world.remove(entityId, MacroRoute::class)
      // The macro graph got it to the neighbourhood; the last stretch is an ordinary local walk.
      return stepToward(context, destination)
    }

    // Only topped up when nearly spent, so a leg is refined once rather than re-searched every tick.
    if (isMoving(world, entityId)) return true

    val leg = navigation.refineLeg(route, from) ?: return false
    return apply(world, entityId, leg)
  }

  private fun apply(world: World, entityId: EntityId, waypoints: List<Vec3L>): Boolean {
    if (waypoints.isEmpty()) return false

    val existing = world.get(entityId, Path::class)
    if (existing == null) {
      world.add(entityId, Path(waypoints.toMutableList()))
    } else {
      existing.setPath(waypoints)
    }

    return true
  }

  /** Whether [target] leaves the creature inside its home range, or at least nearer to it. */
  private fun keepsTerritory(target: Vec3L, from: Vec3L, home: Vec3L, radius: Long): Boolean {
    val reach = target.distance(home)
    return reach <= radius || reach < from.distance(home)
  }

  companion object {
    /**
     * Tiles a creature covers in one wander bout, whatever its territory.
     *
     * Sized for the local pathfinding tier rather than for looks: short enough that `pathTo` answers from
     * its budget instead of falling back to a single checked step. See [wanderStep].
     */
    const val WANDER_STEP_TILES = 6L

    /** The eight walkable neighbours of a tile. Shared, so one per class rather than one per instance. */
    private val DIRECTIONS = listOf(
      Vec3L(-1, -1, 0), Vec3L(0, -1, 0), Vec3L(1, -1, 0),
      Vec3L(-1, 0, 0), Vec3L(1, 0, 0),
      Vec3L(-1, 1, 0), Vec3L(0, 1, 0), Vec3L(1, 1, 0)
    )
  }
}
