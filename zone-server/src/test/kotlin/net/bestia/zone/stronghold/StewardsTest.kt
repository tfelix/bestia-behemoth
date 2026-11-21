package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.stronghold.ecs.Steward
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StewardsTest {

  private val world = testWorld()
  private val west = EmpyriteSeed(rank = 1, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val east = EmpyriteSeed(rank = 2, x = 9_000.0, y = 1_000.0, name = "Goldgrund", settlement = -1)
  private val held = mutableListOf<Stronghold>()

  private val stewards = Stewards(
    strongholds = mockk {
      every { all() } answers { held.toList() }
      every { heldBy(any()) } answers { held.filter { it.guildId == firstArg<Long>() } }
      every { put(any()) } answers { held.removeIf { it.rank == firstArg<Stronghold>().rank }; held.add(firstArg()) }
    },
    seeds = mockk { every { awake() } returns listOf(west, east) },
    spots = mockk {
      every { standingAt(any(), any(), any()) } answers {
        val seed = firstArg<EmpyriteSeed>()
        Vec3L((seed.x + secondArg<Double>()).toLong(), (seed.y + thirdArg<Double>()).toLong(), 40)
      }
    },
    bestias = mockk { every { byIdentifier("guild_steward") } returns mockk { every { id } returns SPECIES_ID } },
    spawner = mockk<BestiaEntitySpawner> {
      every { spawnMob(world, SPECIES_ID, any(), persistent = false) } answers {
        val at = thirdArg<Vec3L>()
        world.createEntity { id -> add(id, Position.fromVec3(at)) }
      }
    },
  )

  @Test
  fun `a hired steward stands beside the Empyrite, and the Stronghold keeps it`() {
    stewards.hire(world, standing(west))

    assertTrue(held.single().hasSteward)
    val steward = stewardsOf(west.rank).single()
    assertEquals(Vec3L(1_003, 1_000, 40), world.get(steward, Position::class)?.toVec3L())
  }

  @Test
  fun `a restart raises the stewards that were hired, and a dismissed one leaves`() {
    held.add(standing(west).copy(hasSteward = true))
    held.add(standing(east))

    stewards.raiseAll(world)
    assertEquals(1, stewardsOf(west.rank).size)
    assertTrue(stewardsOf(east.rank).isEmpty())

    stewards.dismiss(world, west.rank)
    assertTrue(stewardsOf(west.rank).isEmpty())
  }

  @Test
  fun `a fallen member gets up at the guild's nearest Stronghold with a steward`() {
    held.add(standing(west).copy(hasSteward = true))
    held.add(standing(east).copy(hasSteward = true))

    assertEquals(Vec3L(9_003, 998, 40), stewards.respawnPointFor(world, memberAt(Vec3L(8_000, 1_000, 0))))
  }

  @Test
  fun `without a steward, or for another guild, there is no Stronghold to get up at`() {
    held.add(standing(west))

    assertNull(stewards.respawnPointFor(world, memberAt(Vec3L(1_000, 1_000, 0))))
    held[0] = held[0].copy(hasSteward = true)
    assertNull(stewards.respawnPointFor(world, memberAt(Vec3L(1_000, 1_000, 0), guild = OTHER_GUILD)))
  }

  private fun standing(seed: EmpyriteSeed): Stronghold {
    return Stronghold(seed.rank, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)
  }

  private fun memberAt(at: Vec3L, guild: Long = GUILD): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, GuildMembership(guild, taxPercent = 5))
    }
  }

  private fun stewardsOf(rank: Int): List<EntityId> {
    val found = mutableListOf<EntityId>()
    world.query(Steward::class).each { id -> if (get<Steward>().rank == rank) found.add(id) }
    return found
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val SPECIES_ID = 113L
  }
}
