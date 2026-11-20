package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmpyriteSeedSensingTest {

  private val world = testWorld()
  private val config = StrongholdConfig()
  private val awake = listOf(seed(0, 20_000.0), seed(1, 4_000.0), seed(2, 35_000.0), seed(3, 9_000.0), seed(4, 12_000.0))
  private val sent = slot<SMSG>()

  private val sensing = EmpyriteSeedSensing(
    seeds = mockk { every { awake() } returns awake },
    discoveries = mockk { every { isDiscovered(any(), any()) } answers { MASTER_ID == firstArg<Long>() && secondArg<Int>() == 3 } },
    worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 },
    messages = mockk<OutMessageProcessor> { every { sendToPlayer(ACCOUNT_ID, capture(sent)) } returns Unit },
    config = config,
    random = Random(7),
  )

  private val caster = world.createEntity { id ->
    add(id, Account(ACCOUNT_ID))
    add(id, Master(MASTER_ID))
    add(id, Position.fromVec3(Vec3L(0, 0, 0)))
  }

  @Test
  fun `the nearest three seeds in range are shown, nearest first`() {
    sensing.sense(world, caster)

    val marks = (sent.captured as SenseMarksSMSG).marks
    assertEquals(listOf(1L, 3L, 4L), marks.map { it.refId })
    assertTrue(marks.all { it.kind == SenseKind.EMPYRITE_SEED })
  }

  @Test
  fun `a mark is off by at most a third of its distance`() {
    repeat(20) {
      sensing.sense(world, caster)

      for (mark in (sent.captured as SenseMarksSMSG).marks) {
        val seed = awake.single { it.rank.toLong() == mark.refId }
        val distance = hypot(seed.x, seed.y)
        assertTrue(hypot(mark.eastMetres - seed.x, mark.northMetres - seed.y) <= distance * config.senseErrorShare + 1e-6)
      }
    }
  }

  @Test
  fun `only a seed the caster discovered carries its name`() {
    sensing.sense(world, caster)

    val labels = (sent.captured as SenseMarksSMSG).marks.associate { it.refId to it.label }
    assertEquals(mapOf(1L to "", 3L to "Seed 3", 4L to ""), labels)
  }

  private fun seed(rank: Int, east: Double) = EmpyriteSeed(rank, east, 0.0, "Seed $rank", settlement = -1)

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 5L
  }
}
