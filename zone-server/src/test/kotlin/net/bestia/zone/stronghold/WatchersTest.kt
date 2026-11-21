package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.stronghold.ecs.Watcher
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchersTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private var now = Instant.parse("2026-11-21T12:00:00Z")
  private var stronghold = Stronghold(seed.rank, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)
    .withProgress(StrongholdTrack.DEFENSE, TrackProgress(2, 0))

  private val watchers = Watchers(
    strongholds = mockk {
      every { all() } answers { listOf(stronghold) }
      every { on(seed.rank) } answers { stronghold }
      every { put(any()) } answers { stronghold = firstArg() }
    },
    seeds = mockk { every { awake() } returns listOf(seed) },
    spots = mockk {
      every { standingAt(any(), any(), any()) } answers {
        Vec3L((1_000 + secondArg<Double>()).toLong(), (1_000 + thirdArg<Double>()).toLong(), 40)
      }
    },
    bestias = mockk {
      every { byIdentifier("melee_guardian") } returns mockk { every { id } returns MELEE_ID }
      every { byIdentifier("archer_guardian") } returns mockk { every { id } returns ARCHER_ID }
    },
    spawner = mockk<BestiaEntitySpawner> {
      every { spawnMob(world, any(), any(), persistent = false, homePosition = any(), level = any()) } answers {
        val species = arg<Long>(1)
        val at = arg<Vec3L>(2)
        val level = arg<Int>(9)
        world.createEntity { id ->
          add(id, Position.fromVec3(at))
          add(id, Level(level))
          add(id, Species(species))
        }
      }
    },
    worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1L, chunkSize = 32) },
    config = StrongholdConfig(),
    clock = object : Clock() {
      override fun instant() = now
      override fun getZone(): ZoneId = ZoneId.of("UTC")
      override fun withZone(zone: ZoneId?) = this
    },
  )

  @Test
  fun `posts and level grow with Defense`() {
    assertEquals(4, watchers.postsOf(stronghold))
    assertEquals(60, watchers.levelOf(stronghold))
    assertEquals(12, watchers.postsOf(stronghold.withProgress(StrongholdTrack.DEFENSE, TrackProgress(10, 0))))
  }

  @Test
  fun `the leader's mix stands at once, as the guild's watchers at the Stronghold's level`() {
    watchers.setMix(world, stronghold, WatcherKind.MELEE, 2)
    watchers.setMix(world, stronghold, WatcherKind.ARCHER, 1)

    assertEquals(mapOf(WatcherKind.MELEE to 2, WatcherKind.ARCHER to 1), stronghold.watchers)
    val standing = watcherEntities()
    assertEquals(listOf(MELEE_ID, MELEE_ID, ARCHER_ID), standing.map { world.get(it, Species::class)!!.id })
    assertTrue(standing.all { world.get(it, GuildOwned::class) == GuildOwned(GUILD) && world.get(it, Level::class)?.level == 60 })
  }

  @Test
  fun `a fallen watcher comes back after half an hour, or at once when brought back`() {
    watchers.setMix(world, stronghold, WatcherKind.MELEE, 2)
    val (first, second) = watcherEntities()
    world.add(first, Dead())
    world.destroy(second)

    now = now.plusSeconds(10)
    watchers.watch(world)
    assertEquals(2, watchers.fallenOf(seed.rank).size)
    assertTrue(watcherEntities().isEmpty())

    assertTrue(watchers.bringBack(world, stronghold))
    assertEquals(1, watchers.fallenOf(seed.rank).size)

    now = now.plusSeconds(30 * 60)
    watchers.watch(world)
    assertTrue(watchers.fallenOf(seed.rank).isEmpty())
    assertEquals(2, watcherEntities().count { !world.has(it, Dead::class) })
  }

  @Test
  fun `a smaller mix gives up a fallen post first, and a lost Stronghold dismisses them all`() {
    watchers.setMix(world, stronghold, WatcherKind.ARCHER, 2)
    world.destroy(watcherEntities().first())
    watchers.watch(world)

    watchers.setMix(world, stronghold, WatcherKind.ARCHER, 1)
    assertTrue(watchers.fallenOf(seed.rank).isEmpty())
    assertEquals(1, watcherEntities().size)

    watchers.dismissAll(world, seed.rank)
    assertTrue(watcherEntities().isEmpty())
  }

  private fun watcherEntities(): List<EntityId> {
    val found = mutableListOf<EntityId>()
    world.query(Watcher::class).each { id -> if (!world.has(id, Dead::class)) found.add(id) }
    return found
  }

  /** Which species the fake spawner made. */
  private data class Species(val id: Long) : net.bestia.zone.ecs.core.Component

  private companion object {
    const val GUILD = 1L
    const val MELEE_ID = 114L
    const val ARCHER_ID = 115L
  }
}
