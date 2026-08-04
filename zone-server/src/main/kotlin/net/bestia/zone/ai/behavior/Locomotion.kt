package net.bestia.zone.ai.behavior

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.MacroRoute
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
 */
object Locomotion {

  private val DIRECTIONS = listOf(
    Vec3L(-1, -1, 0), Vec3L(0, -1, 0), Vec3L(1, -1, 0),
    Vec3L(-1, 0, 0), Vec3L(1, 0, 0),
    Vec3L(-1, 1, 0), Vec3L(0, 1, 0), Vec3L(1, 1, 0)
  )

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
    val path = context.navigation.pathTo(from, target)
      ?: context.navigation.stepToward(from, target)?.let { listOf(it) }
      ?: return false

    return apply(context.world, context.entityId, path)
  }

  /**
   * Backs away from [threat], preferring whichever legal direction increases the distance most.
   *
   * Tries alternatives rather than only the directly-opposite tile, because the opposite tile is exactly the
   * one most likely to be a wall - something cornered used to give up and stand there being hit.
   */
  fun stepAwayFrom(context: BtContext, threat: Vec3L): Boolean {
    if (isMoving(context.world, context.entityId)) return true

    val from = position(context.world, context.entityId)

    val away = DIRECTIONS
      .map { from + it }
      .filter { it.distance(threat) > from.distance(threat) }
      .sortedByDescending { it.distance(threat) }

    for (candidate in away) {
      val step = context.navigation.stepToward(from, candidate) ?: continue
      return apply(context.world, context.entityId, listOf(step))
    }

    return false
  }

  /**
   * Wanders within [radius] of [home].
   *
   * Picks a destination a few tiles off and paths to it, rather than one adjacent tile per call. Both are
   * random walks, but this one produces a creature that ambles somewhere and then somewhere else, instead of
   * one that jitters between neighbouring tiles - and it costs fewer searches, not more, because one path
   * lasts several tiles.
   */
  fun wanderStep(context: BtContext, home: Vec3L, radius: Long = 5): Boolean {
    if (isMoving(context.world, context.entityId)) return true

    val from = position(context.world, context.entityId)

    // Shuffled and then tried in order: a wander target can land in a rock face, and trying only one
    // candidate per tick makes a creature in broken country look stuck rather than idle.
    val candidates = DIRECTIONS.shuffled(Random.Default).map { direction ->
      val distance = Random.nextLong(1, radius.coerceAtLeast(2))
      Vec3L(
        (from.x + direction.x * distance).coerceIn(home.x - radius, home.x + radius),
        (from.y + direction.y * distance).coerceIn(home.y - radius, home.y + radius),
        from.z
      )
    }

    for (target in candidates) {
      if (target.x == from.x && target.y == from.y) continue
      val path = context.navigation.pathTo(from, target)
        ?: context.navigation.stepToward(from, target)?.let { listOf(it) }
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
        context.navigation.planRoute(from, destination, profileId)?.also { world.add(entityId, it) }
          ?: return false

      context.navigation.shouldReplan(existing, context.currentTick, context.tickRate) ->
        context.navigation.planRoute(from, destination, profileId)?.also { world.add(entityId, it) }
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

    val leg = context.navigation.refineLeg(route, from) ?: return false
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
}
