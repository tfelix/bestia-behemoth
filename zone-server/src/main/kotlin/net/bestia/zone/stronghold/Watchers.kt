package net.bestia.zone.stronghold

import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.stronghold.ecs.Watcher
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/**
 * The watchers of every Stronghold. The leader's mix of kinds is stored with the Stronghold; the watchers
 * themselves, and when a fallen one comes back, live only here. A restart calls every watcher back at once.
 *
 * A watcher roams the ground round its Empyrite and attacks the guild's rivals there. Tick thread only after boot.
 */
@Service
class Watchers(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val spots: SeedSpots,
  private val bestias: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val worldService: WorldService,
  private val statusEffects: StatusEffectService,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** One watcher's place in the Stronghold: who stands in it now, or when a fallen one comes back. */
  private class Post(val kind: WatcherKind, var entityId: EntityId? = null, var backAt: Instant? = null)

  data class Fallen(val kind: WatcherKind, val backAt: Instant)

  private val postsByRank = HashMap<Int, MutableList<Post>>()

  /** Boot only, after the Strongholds and their Empyrites. */
  fun raiseAll(world: World) {
    strongholds.all().filter { it.state == State.STANDING }.forEach { call(world, it) }
  }

  fun postsOf(stronghold: Stronghold): Int {
    val defense = stronghold.progressOf(StrongholdTrack.DEFENSE).level

    return (config.watcherBasePosts + defense).coerceAtMost(config.watcherMaxPosts)
  }

  fun levelOf(stronghold: Stronghold): Int {
    return config.watcherBaseLevel + config.watcherLevelPerDefense * stronghold.progressOf(StrongholdTrack.DEFENSE).level
  }

  /** The leader changed the mix by one: the Stronghold keeps it, and the posts follow at once. */
  fun setMix(world: World, stronghold: Stronghold, kind: WatcherKind, count: Int) {
    val changed = stronghold.withWatchers(kind, count)
    strongholds.put(changed)
    call(world, changed)
  }

  /** Fills every empty post of the mix and gives up the posts the mix no longer has. */
  fun call(world: World, stronghold: Stronghold) {
    val posts = postsByRank.getOrPut(stronghold.rank) { ArrayList() }

    for (kind in WatcherKind.entries) {
      val ofKind = posts.filter { it.kind == kind }
      val wanted = stronghold.watchersOf(kind)

      // A fallen post goes first, so a smaller mix sends nobody standing away while one is down.
      ofKind.sortedBy { if (it.entityId == null) 1 else 0 }.drop(wanted).forEach { surplus ->
        surplus.entityId?.let { if (world.isAlive(it)) world.destroy(it) }
        posts.remove(surplus)
      }
      repeat(wanted - ofKind.size) { posts.add(Post(kind)) }
    }

    posts.filter { it.entityId == null && it.backAt == null }.forEach { raise(world, stronghold, it, posts.indexOf(it)) }
  }

  /** Marks the watchers that fell, and calls back those whose time is up. */
  fun watch(world: World) {
    val now = clock.instant()

    for ((rank, posts) in postsByRank) {
      val stronghold = strongholds.on(rank)?.takeIf { it.state == State.STANDING } ?: continue
      posts.forEachIndexed { index, post ->
        val standing = post.entityId
        if (standing != null && (!world.isAlive(standing) || world.has(standing, Dead::class))) {
          post.entityId = null
          post.backAt = now.plusSeconds(config.watcherReturnMinutes * 60)
        }
        if (post.backAt?.isAfter(now) == false) raise(world, stronghold, post, index)
      }
    }
  }

  /** Who is down and when they come back, soonest first. */
  fun fallenOf(rank: Int): List<Fallen> {
    return postsByRank[rank].orEmpty().mapNotNull { post -> post.backAt?.let { Fallen(post.kind, it) } }.sortedBy { it.backAt }
  }

  /** Calls back the watcher that would wait longest. False when nobody is down. */
  fun bringBack(world: World, stronghold: Stronghold): Boolean {
    val posts = postsByRank[stronghold.rank] ?: return false
    val longestWait = posts.filter { it.backAt != null }.maxByOrNull { it.backAt!! } ?: return false
    raise(world, stronghold, longestWait, posts.indexOf(longestWait))

    return true
  }

  /** A new Defense level: the watchers standing are called again at the new level, where they stand. */
  fun levelUp(world: World, stronghold: Stronghold) {
    postsByRank[stronghold.rank].orEmpty().forEachIndexed { index, post ->
      val standing = post.entityId?.takeIf { world.isAlive(it) } ?: return@forEachIndexed
      val at = world.get(standing, Position::class)?.toVec3L()
      world.destroy(standing)
      raise(world, stronghold, post, index, at)
    }
  }

  /** The guild's Strengthen Watchers level changed: the Stronghold keeps it, and every standing watcher gets it. */
  fun strengthen(world: World, stronghold: Stronghold, level: Int) {
    if (level <= stronghold.watcherStrength) return

    val strengthened = stronghold.copy(watcherStrength = level)
    strongholds.put(strengthened)
    postsByRank[stronghold.rank].orEmpty().forEach { post ->
      post.entityId?.takeIf { world.isAlive(it) }?.let { applyStrength(world, it, level) }
    }
  }

  /** The Stronghold fell or changed hands: its watchers leave with the old guild. */
  fun dismissAll(world: World, rank: Int) {
    postsByRank.remove(rank).orEmpty().forEach { post -> post.entityId?.let { if (world.isAlive(it)) world.destroy(it) } }
  }

  private fun raise(world: World, stronghold: Stronghold, post: Post, index: Int, at: Vec3L? = null) {
    val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: return
    val home = spots.standingAt(seed)
    val roamTiles = (config.watcherRoamMetres / worldService.config.voxelSize).toLong()

    val id = spawner.spawnMob(
      world,
      bestias.byIdentifier(post.kind.species).id,
      at ?: postAt(seed, index),
      persistent = false,
      homePosition = home,
      level = levelOf(stronghold),
    )
    world.add(id, GuildOwned(stronghold.guildId))
    world.add(id, Watcher(stronghold.rank))
    applyStrength(world, id, stronghold.watcherStrength)
    world.get(id, AiAgent::class)?.memory?.let { memory ->
      memory.set(BestiaDomain.GUARD_AREA, WorkArea(home, roamTiles), Blackboard.PERMANENT)
      memory.set(BestiaDomain.WANDER_RADIUS, roamTiles, Blackboard.PERMANENT)
    }

    post.entityId = id
    post.backAt = null
  }

  private fun applyStrength(world: World, watcherId: EntityId, level: Int) {
    if (level > 0) statusEffects.applyEffect(world, watcherId, StatusEffectId.WATCHER_STRENGTH, level)
  }

  /** Spread on a small ring round the Empyrite, so they do not all start in one spot. */
  private fun postAt(seed: EmpyriteSeed, index: Int): Vec3L {
    val angle = index * GOLDEN_ANGLE

    return spots.standingAt(seed, START_RING_METRES * cos(angle), START_RING_METRES * sin(angle))
  }

  private companion object {
    const val START_RING_METRES = 8.0
    const val GOLDEN_ANGLE = 2.399963
  }
}
