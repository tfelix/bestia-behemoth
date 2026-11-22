package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.GroundsStructureSystem
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrongholdGroundsTest {

  private val world = testWorld()
  private val held = mutableListOf(Stronghold(rank = 1, GUILD, State.SITE, 40L, Instant.EPOCH, Instant.EPOCH))
  private val plantedSeed = EmpyriteSeed(rank = 1, x = 10_000.0, y = 10_000.0, name = "Hochvein", settlement = -1)
  private val freeSeed = EmpyriteSeed(rank = 2, x = 20_000.0, y = 10_000.0, name = "Goldgrund", settlement = -1)

  private val grounds = StrongholdGrounds(
    strongholds = mockk {
      every { on(any()) } answers { held.firstOrNull { it.rank == firstArg<Int>() } }
      every { heldBy(any()) } answers { held.filter { it.guildId == firstArg<Long>() } }
    },
    seeds = mockk { every { awake() } returns listOf(plantedSeed, freeSeed) },
    worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1L, chunkSize = 32) },
    config = StrongholdConfig(coreRadiusMetres = 5.0, groundsRadiusMetres = 500.0),
  )

  @Test
  fun `a member is inside on the grounds of a standing Empyrite of the guild`() {
    held[0] = held[0].copy(state = State.STANDING)

    assertTrue(grounds.isInOwnStronghold(world, entityAt(10_400), GUILD))
    assertFalse(grounds.isInOwnStronghold(world, entityAt(10_600), GUILD))
    assertFalse(grounds.isInOwnStronghold(world, entityAt(10_400), OTHER_GUILD))
  }

  @Test
  fun `a site is not a Stronghold yet`() {
    assertFalse(grounds.isInOwnStronghold(world, entityAt(10_000), GUILD))
  }

  @Test
  fun `a seed's core holds against every dig, and nobody digs on a planted seed's grounds`() {
    assertEquals(OpError.GROUND_SEED_CORE_HOLDS, grounds.digRefusal(Vec3L(20_003, 10_000, 40), 1.0))
    assertEquals(OpError.GROUND_SEED_CORE_HOLDS, grounds.digRefusal(Vec3L(10_000, 10_004, -200), 1.0))
    assertEquals(OpError.GROUND_STRONGHOLD_NO_DIG, grounds.digRefusal(Vec3L(10_400, 10_000, 40), 1.0))
    assertEquals(OpError.GROUND_STRONGHOLD_NO_DIG, grounds.digRefusal(Vec3L(10_503, 10_000, 40), 4.0), "a blast reaching in")
    assertNull(grounds.digRefusal(Vec3L(20_300, 10_000, 40), 1.0), "a free seed's surroundings are ordinary ground")
    assertNull(grounds.digRefusal(Vec3L(10_600, 10_000, 40), 1.0))
  }

  @Test
  fun `nobody builds on a core, and only the holding guild builds on the grounds`() {
    val member = builder(GUILD)
    val rival = builder(OTHER_GUILD)

    assertEquals(OpError.BUILD_ON_SEED_CORE, grounds.buildRefusal(world, member, Vec3L(10_002, 10_000, 40)))
    assertNull(grounds.buildRefusal(world, member, Vec3L(10_100, 10_000, 40)))
    assertEquals(OpError.BUILD_ON_STRONGHOLD_GROUNDS, grounds.buildRefusal(world, rival, Vec3L(10_100, 10_000, 40)))
    assertNull(grounds.buildRefusal(world, rival, Vec3L(20_100, 10_000, 40)))
  }

  @Test
  fun `a building on the grounds is the holding guild's, and one off them is nobody's`() {
    val onGrounds = structureAt(Vec3L(10_200, 10_000, 40))
    val offGrounds = structureAt(Vec3L(11_000, 10_000, 40))
    world.add(offGrounds, GuildOwned(OTHER_GUILD))

    GroundsStructureSystem(grounds).update(world, 2f)

    assertEquals(GuildOwned(GUILD), world.get(onGrounds, GuildOwned::class))
    assertNull(world.get(offGrounds, GuildOwned::class))
  }

  @Test
  fun `only the holding guild repairs buildings on the grounds`() {
    assertTrue(grounds.mayRepairAt(world, builder(GUILD), Vec3L(10_100, 10_000, 40)))
    assertFalse(grounds.mayRepairAt(world, builder(OTHER_GUILD), Vec3L(10_100, 10_000, 40)))
    assertFalse(grounds.mayRepairAt(world, builder(GUILD), Vec3L(11_000, 10_000, 40)), "off the grounds")
  }

  private fun entityAt(east: Long) = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(east, 10_000, 0))) }

  private fun builder(guildId: Long): EntityId {
    return world.createEntity { id -> add(id, GuildMembership(guildId, taxPercent = 5)) }
  }

  private fun structureAt(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, PropPose(at, yaw = 0f))
      add(id, PlayerStructureIdentity(structureId = id, ownerAccountId = 7L, ownerMasterId = 1L))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
