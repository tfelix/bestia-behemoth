package net.bestia.zone.ecs.spawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import kotlin.random.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps every stocked den supplied, and lets the rest sleep.
 *
 * ### Dormancy is the whole point
 *
 * `worldgen`'s spawner stage puts some thirty thousand dens on the 128 km world and half a million on a
 * 512 km one. A pack of ten each would be a third of a million entities, nearly all of them out of anybody's
 * sight. So a den does nothing until a player is inside its [Spawner.activationRange].
 *
 * ### Finding the few without looking at the many
 *
 * This system used to sweep every den on the world on every tick and distance-test each one. That is tens of
 * thousands of component fetches sixty times a second to discover that two dens are near a player. The work is
 * now split the way a physics broadphase is:
 *
 *  1. **Broad phase** - [SpawnerCellIndex] returns the dens in the 3x3 cells around each player. A few hash
 *     probes, no traversal, no lock, and it is built once at boot because dens never move.
 *  2. **Narrow phase** - the exact, per-den [withinActivation] test, over that handful rather than over the
 *     world.
 *  3. **Diff** - against [stocked], so the transition into and out of activity is found without a scan. This
 *     is what the old `wasAwake` flag on the component was for; a set of ids does the same job without a
 *     second source of truth, and without needing every den in the world consulted to find the few that
 *     changed.
 *
 * ### Why a den lingers after the last player leaves
 *
 * Tearing a pack down the instant a player steps outside the radius means a player pacing a boundary destroys
 * and rebuilds it repeatedly, and a player who steps away for five seconds returns to fresh creatures with no
 * aggro, no damage taken and no memory of them. So a den that loses its last player is only marked *idle*, and
 * is torn down only if it is still idle [UNLOAD_DELAY_SECONDS] later. This is the same hysteresis the grid
 * unload delay gives a WoW-style server, and it costs one timestamp per stocked den.
 *
 * ### Where a rehydrated pack comes in
 *
 * A den's creatures are `Persistent`, so a pack alive at shutdown comes back at the next boot. It comes back
 * *before* this system has ever seen the den, though, and a den nobody is standing near never reaches the
 * broad phase - so a restored pack would belong to no den and never be torn down. [adoptRehydratedPack] is
 * the second, boot-only producer of [stocked] that closes this: `DenPackRestoreService` hands the den over
 * once, and from there the ordinary idle-and-expire path owns it. The three phases above are untouched.
 *
 * ### Three bugs this file used to have
 *
 * All were the kind that look like working code. `despawnPack` destroyed its creatures without telling
 * `PersistedEntityDeletionQueue`, so **every dormant den left its whole pack behind in the database** to be
 * rehydrated at the next boot as creatures nothing owned - on top of the fresh pack the den then made.
 * `spawnMissingEntities` called `spawnMob(world, "blob", ...)` with a literal, so **every den in the world
 * produced blobs** whatever its `bestiaId` said - which would have made the entire level ramp invisible while
 * the spawn system appeared to work perfectly. And it spawned at `z = 0`, sea level, which for a den on a
 * hillside is underground.
 *
 * The height fix is deliberately **not** a call to `ChunkService` from here.
 * `ChunkStreamSystem.groundNewcomers` already snaps anything without a `Grounded` marker onto the surface on
 * the tick after it appears, and two mechanisms for "put this entity on the ground" is one too many. What this
 * passes instead is the **den's own z**, which `WildSpawnerService` took from the terrain when it placed the
 * den - so a creature starts within a voxel or two of its final height and is exact one tick later.
 *
 * That approximation got looser when [Spawner.range] went from 140 m to several hundred, which is what makes a
 * den read as populated country rather than as a huddle: a pack now spreads far enough that the den's own
 * height is a poorer guess for its far corner, and a creature can be placed over a lake or against a cliff.
 * `groundNewcomers` still puts it on the surface a tick later, so the failure mode is a creature standing
 * somewhere odd rather than one underground - but nothing checks the ground under a *spawn point* the way
 * `Invariants.checkSpawnersAreOnDryLand` checks the den's own centre.
 */
@SpringComponent
@Order(80)
class SpawnerSystem(
  private val bestiaEntitySpawner: BestiaEntitySpawner,
  private val cellIndex: SpawnerCellIndex,
  private val deletionQueue: PersistedEntityDeletionQueue,
) : System {

  /**
   * Activation is a coarse gate, and [Spawner.activationRange] is deliberately wider than a player's view
   * radius so a den has already stocked itself by the time anybody can see it. A quarter second of latency
   * disappears into that margin, and tick-rate resolution buys nothing for it.
   */
  override val schedule: Schedule = Schedule.EverySeconds(0.25f)

  /**
   * [Position] is read by [activePlayerPositions] and has to be declared, not just used: `SystemScheduler`
   * decides what may run in parallel purely from these sets, and it is this entry that keeps `MoveSystem`,
   * which writes `Position`, out of the same wave.
   */
  override val reads: ComponentClassSet = setOf(ActivePlayer::class, Position::class)
  override val writes: ComponentClassSet = setOf(Spawner::class)

  /**
   * Dens with a live pack right now.
   *
   * Not "a den with a player near it" - a den stays in here through its unload delay, after the last player
   * has gone. What this set exists for is the *transition*: taking a pack back out of the world is something
   * that happens once, when a den stops being wanted, and "it had a pack and now should not" cannot be read
   * off a distance. Kept as ids here rather than as a flag on each component, because a flag can only be
   * found by looking at every den, which is the sweep this system exists to avoid.
   */
  private val stocked = HashSet<EntityId>()

  /**
   * Stocked dens with no player in range, and the [elapsed] stamp at which that became true.
   *
   * Bounded by dens-in-range rather than by world size: an id enters only from [stocked], and leaves it in
   * the same step it leaves [stocked].
   */
  private val idleSince = HashMap<EntityId, Float>()

  /**
   * Simulated seconds since boot, the clock [idleSince] is stamped against.
   *
   * Simulated rather than wall clock so the delay is deterministic under test, and consistent with
   * [Schedule.EverySeconds]' own semantics. Accumulating the delta is correct even though this system skips
   * ticks: `SystemScheduler` passes the elapsed time since the system *last ran*, not the time since the last
   * tick, precisely so systems that integrate over time stay right whatever their cadence.
   */
  private var elapsed = 0f

  override fun update(world: World, deltaTime: Float) {
    elapsed += deltaTime

    // Collected once per pass rather than queried per den. With a thousand dens and a handful of players, the
    // other order asks the same question a thousand times.
    val players = activePlayerPositions(world)

    // Broad phase. One shared set, so several players standing together cost one union rather than one
    // collection each.
    val candidates = HashSet<EntityId>()
    for (player in players) {
      cellIndex.collectNear(player, candidates)
    }

    // Narrow phase: the exact per-den test, over the handful the cells returned. `desired` is the single
    // producer of "this den should have a pack" - anything else that ever wants to hold a den awake (a quest,
    // a world event) unions into it here and needs no other change.
    val desired = HashSet<EntityId>(candidates.size)
    for (id in candidates) {
      val spawner = world.get(id, Spawner::class) ?: continue
      if (players.any { withinActivation(spawner, it) }) {
        desired.add(id)
      }
    }

    for (id in desired) {
      val spawner = world.get(id, Spawner::class) ?: continue
      idleSince.remove(id)
      stocked.add(id)
      removeDeadEntities(spawner, world)
      spawnMissingEntities(spawner, world)
    }

    // `putIfAbsent`, so the stamp records when the den *became* idle rather than the last time we noticed it
    // still was - otherwise the delay would never expire.
    for (id in stocked) {
      if (id !in desired) {
        idleSince.putIfAbsent(id, elapsed)
      }
    }

    expireIdleDens(world)
  }

  /** Takes the pack out of any den that has now been idle for the whole delay. */
  private fun expireIdleDens(world: World) {
    val iterator = idleSince.entries.iterator()
    while (iterator.hasNext()) {
      val (id, since) = iterator.next()
      if (elapsed - since < UNLOAD_DELAY_SECONDS) continue

      iterator.remove()
      stocked.remove(id)
      world.get(id, Spawner::class)?.let { despawnPack(it, world) }
    }
  }

  /**
   * Where the players who have actually picked a master are standing.
   *
   * The same set `ChunkStreamSystem` anchors chunk streaming on, and queried the same way: an account that has
   * not chosen one is not standing anywhere, so it must not wake a den.
   */
  private fun activePlayerPositions(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, ActivePlayer::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }

  /**
   * Horizontal distance only, and squared so nothing takes a root.
   *
   * Horizontal because a den and a player on the same hillside can be a hundred metres apart vertically while
   * in plain sight of one another, and a gallery forty metres down is not in sight of the surface either.
   * Height is the wrong axis for "can this be seen"; the right answer to that is line of sight, which is not
   * this system's business. [SpawnerCellIndex] keys on x and y alone for the same reason.
   */
  private fun withinActivation(spawner: Spawner, player: Vec3L): Boolean {
    val dx = player.x - spawner.position.x
    val dy = player.y - spawner.position.y
    val reach = spawner.activationRange.toLong()
    return dx * dx + dy * dy <= reach * reach
  }

  private fun spawnMissingEntities(spawner: Spawner, world: World) {
    if (spawner.spawnedEntities.size >= spawner.maxSpawnCount) {
      return
    }

    val x = randomBetween(spawner.position.x - spawner.range / 2, spawner.position.x + spawner.range / 2)
    val y = randomBetween(spawner.position.y - spawner.range / 2, spawner.position.y + spawner.range / 2)

    // `spawner.bestiaId`, not a literal, and the den's own z, not zero. See the class KDoc.
    val spawnedEntityId = bestiaEntitySpawner.spawnMob(
      world,
      spawner.bestiaId,
      Vec3L(x, y, spawner.position.z),
      den = DenMember(spawner.identity)
    )

    spawner.spawnedEntities.add(spawnedEntityId)
  }

  /**
   * Takes the pack back out of the world, so the den restocks fresh rather than resuming half full.
   *
   * The queue entry is the half that used to be missing. A creature carries `Persistent`, so it has a row
   * in `entity` the moment the persistence sweep next runs; destroying it without saying so left that row
   * behind to be rehydrated at the next boot as a creature the den had already forgotten. The den restocks
   * on top of it, and the population grows every restart.
   *
   * Enqueued **unconditionally**, outside the liveness guard: a member `DeathSystem` destroyed earlier this
   * tick is already gone from the world and still has a row, and a delete for an id that has none is a
   * no-op.
   */
  private fun despawnPack(spawner: Spawner, world: World) {
    if (spawner.spawnedEntities.isEmpty()) return

    LOG.debug { "Den at ${spawner.position} went dormant; despawning ${spawner.spawnedEntities.size}" }
    for (entityId in spawner.spawnedEntities) {
      deletionQueue.enqueue(entityId)
      if (world.hasEntity(entityId)) world.destroy(entityId)
    }
    spawner.spawnedEntities.clear()
  }

  /**
   * Takes responsibility for a den whose pack was rehydrated from storage rather than spawned here.
   *
   * [stocked] is otherwise seeded only from `desired`, which is only reached through the broad phase, which
   * only fires for a den near a player. A den restored at boot has a live pack and, almost always, nobody
   * anywhere near it - so without this it would sit outside every set this system keeps: never restocked,
   * never counted, and, the part that matters, **never torn down**. Its creatures would outlive every
   * restart.
   *
   * Adding it to [stocked] alone is enough, and that is the point of doing it this way. On the next pass
   * the den is not in `desired`, so the existing `putIfAbsent` stamps it idle and [expireIdleDens] despawns
   * it [UNLOAD_DELAY_SECONDS] later down exactly the path a den a player walked away from takes. If a
   * player does arrive first, `desired` wins, the stamp is dropped, and [spawnMissingEntities] tops the
   * rehydrated pack up to size instead of rebuilding it. [update] needs no change at all, and there is no
   * scan - this system is *told*, once, rather than made to go looking.
   *
   * Boot-time only, from [DenPackRestoreService], and it writes a plain `HashSet` from the boot thread.
   * That is safe because `CommandLineRunner`s are sequential and `WorldBootRunner` starts the tick loop
   * strictly later - not because the set is thread-safe. Deliberately not made concurrent: that would hide
   * the constraint and charge every tick for a boot-time convenience.
   */
  fun adoptRehydratedPack(denEntityId: EntityId) {
    stocked.add(denEntityId)
  }

  private fun removeDeadEntities(spawner: Spawner, world: World) {
    spawner.spawnedEntities.removeIf { entityId -> !world.hasEntity(entityId) }
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Ceiling on any den's [Spawner.activationRange], in world units - one of which is one metre, so this is
     * about a kilometre.
     *
     * It exists to size [SpawnerCellIndex]'s cells: the broad phase looks at the 3x3 cells around a player, so
     * a den whose range reached beyond one cell would be missed at the far edge of that range. Enforced in
     * [Spawner]'s init block, because a den that quietly never woke would look like a content bug rather than
     * a configuration one.
     */
    const val MAX_ACTIVATION_RANGE = 1024

    /**
     * How long a den keeps its pack after the last player leaves.
     *
     * A minute, which is long enough that walking out of a camp and back does not reset the creatures in it,
     * and short enough that a wilderness a player crossed an hour ago is not still populated.
     */
    const val UNLOAD_DELAY_SECONDS = 60f
  }
}
