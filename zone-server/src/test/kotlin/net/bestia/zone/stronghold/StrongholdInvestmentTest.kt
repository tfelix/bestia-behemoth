package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrongholdInvestmentTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private var stronghold = Stronghold(seed.rank, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)
  private val empyrites = mockk<Empyrites>(relaxed = true)
  private val stewards = mockk<Stewards>(relaxed = true)
  private val sent = slot<SMSG>()
  private val messages = mockk<OutMessageProcessor> { every { sendToPlayer(ACCOUNT_ID, capture(sent)) } returns Unit }

  private fun investment(roll: Double = 0.9) = StrongholdInvestment(
    strongholds = mockk {
      every { on(seed.rank) } answers { stronghold }
      every { put(any()) } answers { stronghold = firstArg() }
    },
    seeds = mockk { every { awake() } returns listOf(seed) },
    empyrites = empyrites,
    stewards = stewards,
    coins = coinsPaidWith(COIN),
    messages = messages,
    config = StrongholdConfig(stepCoinsPerLevel = 20_000, doubleStepChance = 0.5, stewardCoins = 200_000),
    random = object : Random() {
      override fun nextBits(bitCount: Int) = 0
      override fun nextDouble() = roll
    },
  )

  private val empyrite = world.createEntity { id ->
    add(id, Position.fromVec3(Vec3L(1_000, 1_000, 0)))
    add(id, Empyrite(seed.rank))
    add(id, Health(current = 41_200, max = 60_000))
  }

  @Test
  fun `a member's click opens the window with every track and its next price`() {
    stronghold = stronghold.withProgress(StrongholdTrack.DEFENSE, TrackProgress(1, 2))

    assertNull(investment().open(world, memberAt(Vec3L(1_002, 1_000, 0)), empyrite))

    val window = sent.captured as StrongholdSMSG
    assertEquals("Hochvein", window.seedName)
    assertEquals(41_200 to 60_000, window.hp to window.maxHp)
    assertEquals(
      listOf(TrackProgress(1, 2) to 40_000L, TrackProgress() to 20_000L, TrackProgress() to 20_000L),
      window.tracks.map { it.progress to it.nextStepCoins }
    )
  }

  @Test
  fun `the window opens only for the guild, and only close by`() {
    val outsider = memberAt(Vec3L(1_000, 1_000, 0), guild = OTHER_GUILD)
    val farOff = memberAt(Vec3L(1_010, 1_000, 0))

    assertEquals(OpError.STRONGHOLD_NOT_YOUR_GUILDS, investment().open(world, outsider, empyrite))
    assertEquals(OpError.STRONGHOLD_OUT_OF_RANGE, investment().open(world, farOff, empyrite))
  }

  @Test
  fun `a step is paid from the bag and moves the track on`() {
    val member = memberAt(Vec3L(1_000, 1_000, 0), coins = 25_000)

    assertNull(investment().invest(world, member, empyrite, StrongholdTrack.STORAGE))

    assertEquals(TrackProgress(0, 1), stronghold.progressOf(StrongholdTrack.STORAGE))
    assertEquals(5_000, world.get(member, Inventory::class)!!.getItems().sumOf { it.amount })
  }

  @Test
  fun `with Permanent Development a step may count twice`() {
    val member = memberAt(Vec3L(1_000, 1_000, 0), coins = 25_000, permanentDevelopment = true)

    investment(roll = 0.1).invest(world, member, empyrite, StrongholdTrack.ECONOMY)

    assertEquals(TrackProgress(0, 2), stronghold.progressOf(StrongholdTrack.ECONOMY))
  }

  @Test
  fun `a new Defense level hardens the Empyrite`() {
    stronghold = stronghold.withProgress(StrongholdTrack.DEFENSE, TrackProgress(0, 4))

    investment().invest(world, memberAt(Vec3L(1_000, 1_000, 0), coins = 20_000), empyrite, StrongholdTrack.DEFENSE)

    assertEquals(TrackProgress(1, 0), stronghold.progressOf(StrongholdTrack.DEFENSE))
    verify { empyrites.harden(world, seed.rank) }
  }

  @Test
  fun `a step needs its coins, and a track ends at level ten`() {
    val poor = memberAt(Vec3L(1_000, 1_000, 0), coins = 19_999)
    assertEquals(OpError.STRONGHOLD_CANNOT_AFFORD_STEP, investment().invest(world, poor, empyrite, StrongholdTrack.STORAGE))

    stronghold = stronghold.withProgress(StrongholdTrack.STORAGE, TrackProgress(10, 0))
    val rich = memberAt(Vec3L(1_000, 1_000, 0), coins = 1_000_000)
    assertEquals(OpError.REQUEST_REFUSED, investment().invest(world, rich, empyrite, StrongholdTrack.STORAGE))
  }

  @Test
  fun `with Steward Contract, a member pays for a steward and the window shows it`() {
    val member = memberAt(Vec3L(1_000, 1_000, 0), coins = 250_000, stewardContract = true)
    every { stewards.hire(world, any()) } answers { stronghold = secondArg<Stronghold>().copy(hasSteward = true) }

    assertNull(investment().hireSteward(world, member, empyrite))

    verify { stewards.hire(world, match { it.rank == seed.rank }) }
    assertEquals(50_000, world.get(member, Inventory::class)!!.getItems().sumOf { it.amount })
    assertEquals(StrongholdSMSG.Steward(hasContract = true, isHired = true, coins = 200_000), (sent.captured as StrongholdSMSG).steward)
  }

  @Test
  fun `a steward needs the contract, its coins, and a Stronghold without one`() {
    val noContract = memberAt(Vec3L(1_000, 1_000, 0), coins = 250_000)
    val poor = memberAt(Vec3L(1_000, 1_000, 0), coins = 199_999, stewardContract = true)
    val rich = memberAt(Vec3L(1_000, 1_000, 0), coins = 250_000, stewardContract = true)

    assertEquals(OpError.REQUEST_REFUSED, investment().hireSteward(world, noContract, empyrite))
    assertEquals(OpError.STRONGHOLD_CANNOT_AFFORD_STEWARD, investment().hireSteward(world, poor, empyrite))
    stronghold = stronghold.copy(hasSteward = true)
    assertEquals(OpError.REQUEST_REFUSED, investment().hireSteward(world, rich, empyrite))
    verify(exactly = 0) { stewards.hire(any(), any()) }
  }

  private fun memberAt(
    at: Vec3L,
    guild: Long = GUILD,
    coins: Int = 0,
    permanentDevelopment: Boolean = false,
    stewardContract: Boolean = false,
  ): EntityId {
    val skills = buildMap {
      if (permanentDevelopment) put(SkillId.PERMANENT_DEVELOPMENT.name, 1)
      if (stewardContract) put(SkillId.STEWARD_CONTRACT.name, 1)
    }

    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Master(5L))
      add(id, Account(ACCOUNT_ID))
      add(id, GuildMembership(guild, taxPercent = 5, guildName = "Iron Wolves", skillLevels = skills))
      add(id, Inventory(mutableListOf(Inventory.Item(COIN, coins))))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val ACCOUNT_ID = 7L
    const val COIN = 3L
  }
}
