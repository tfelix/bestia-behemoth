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
 * [stepToward] and [wanderLeg] are the *local* tier: a considered path over nearby ground. Only [stepToward]
 * falls back to a single checked step when the tick's search budget is spent, because only a chase is worth
 * that step's cost - see [wanderLeg]. [travelTo] is the *macro* tier, for a destination too far away to
 * search column by column - it plans over the world's node graph and refines one leg at a time as the
 * traveller arrives.
 *
 * A leaf that cannot move gets `false` and decides for itself what that means; nothing here fails silently by
 * leaving an entity standing still with no way to tell.
 *
 * Holds its [navigation] dependency rather than reading it off the tick context, so a movement leaf declares
 * what it needs and can be handed a fake in a test. The action template that grounds a movement action owns
 * one of these and passes it to the leaves it builds.
 *
 * [random] is held for the same reason, and it is not a nicety: where a wandering creature goes has to be
 * reachable from a test, so a scenario that pins the world, the clock and the navigation can also say where
 * the mob will be two seconds later. A test that wants a reproducible walk passes a seeded [Random]; the
 * server passes nothing and gets the process-global one.
 */
class Locomotion(
  private val navigation: NavigationService,
  private val random: Random = Random.Default
) {

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
   * Starts one roaming leg: a navigated path to a spot a few tiles off that keeps the creature within
   * [radius] of [home].
   *
   * No greedy fallback, unlike [stepToward]. A leg that cannot be pathed is no leg at all, because a
   * one-tile walk costs every observer the same `Path` message and stop message as a real one and is spent
   * again a quarter of a second later. The caller pauses and asks again - see
   * [net.bestia.zone.ai.bt.leaves.Wander].
   *
   * [radius] has no default so that `BestiaDomain` stays the one place a territory size is decided; this
   * package knows nothing about the bestia domain.
   *
   * @return whether the entity is now walking a leg
   */
  fun wanderLeg(context: BtContext, home: Vec3L, radius: Long): Boolean {
    if (isMoving(context.world, context.entityId)) return true

    val from = position(context.world, context.entityId)
    var searches = 0

    // Shuffled and then tried in order: a leg can land in a rock face, and one candidate per attempt would
    // make a creature in broken country look stuck rather than idle. Bounded because each search that is
    // actually run charges the tick's global pathfinding budget.
    for (direction in DIRECTIONS.shuffled(random)) {
      if (searches >= MAX_LEG_SEARCHES) break

      val target = legTarget(from, direction, radius)
      if (target.x == from.x && target.y == from.y) continue
      if (!keepsTerritory(target, from, home, radius)) continue

      searches++
      val path = navigation.pathTo(from, target) ?: continue

      return apply(context.world, context.entityId, path)
    }

    return false
  }

  /** A spot [WANDER_LEG_MIN_TILES]..[WANDER_LEG_MAX_TILES] along [direction], never further than [radius]. */
  private fun legTarget(from: Vec3L, direction: Vec3L, radius: Long): Vec3L {
    val longest = minOf(WANDER_LEG_MAX_TILES, radius)
    val shortest = minOf(WANDER_LEG_MIN_TILES, longest)
    val tiles = random.nextLong(shortest, longest + 1)

    return Vec3L(
      from.x + direction.x * tiles,
      from.y + direction.y * tiles,
      from.z
    )
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

  /**
   * Whether [target] leaves the creature inside its home range, or at least nearer to it.
   *
   * A disc rather than a box, because [Vec3L.distance] is the same horizontal measure `Goals.RETURN_HOME`
   * tests: a target this accepted but that goal considered out of range would put the creature in a loop.
   * "Nearer to it" is what keeps a creature that starts outside its range from having no legal candidate.
   */
  private fun keepsTerritory(target: Vec3L, from: Vec3L, home: Vec3L, radius: Long): Boolean {
    val reach = target.distance(home)
    return reach <= radius || reach < from.distance(home)
  }

  companion object {
    /**
     * Shortest and longest roaming leg, in tiles, whatever the creature's territory.
     *
     * Sized for the local pathfinding tier rather than for looks: short enough that `pathTo` answers from
     * its per-tick budget. Territory size and stride are separate questions, so the draw is not taken off
     * the radius - it is only capped by it.
     */
    const val WANDER_LEG_MIN_TILES = 3L
    const val WANDER_LEG_MAX_TILES = 4L

    /** Searches one leg may cost, so a hemmed-in creature cannot spend the whole tick's budget by itself. */
    private const val MAX_LEG_SEARCHES = 3

    /** The eight walkable neighbours of a tile. Shared, so one per class rather than one per instance. */
    private val DIRECTIONS = listOf(
      Vec3L(-1, -1, 0), Vec3L(0, -1, 0), Vec3L(1, -1, 0),
      Vec3L(-1, 0, 0), Vec3L(1, 0, 0),
      Vec3L(-1, 1, 0), Vec3L(0, 1, 0), Vec3L(1, 1, 0)
    )
  }
}
