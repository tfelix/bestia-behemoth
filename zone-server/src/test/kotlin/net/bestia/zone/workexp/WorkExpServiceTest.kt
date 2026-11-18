package net.bestia.zone.workexp

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.level.LevelUpExperienceCalculator
import net.bestia.zone.config.WorkExpConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.spawn.ecs.KillExpLevy
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

class WorkExpServiceTest {

  private val config = WorkExpConfig()
  private val calculator = WorkExpCalculator(config, LevelUpExperienceCalculator())
  private val sessions = mockk<ConnectionInfoService>(relaxed = true)
  private val clock = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC)
  private val world = testWorld()

  private fun service(vararg levies: KillExpLevy) =
    WorkExpService(calculator, config, MasterEntityResolver(sessions), levies.toList(), clock)

  private fun master(level: Int): EntityId = world.createEntity { id ->
    add(id, Master(masterId = MASTER_ID, name = "Smith"))
    add(id, Account(accountId = ACCOUNT_ID))
    add(id, Level(level))
  }

  private fun bestia(): EntityId = world.createEntity { id ->
    add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = 13L))
    add(id, Account(accountId = ACCOUNT_ID))
  }

  private fun gained(id: EntityId): Int? = world.get(id, GainExp::class)?.value

  @Test
  fun `a master is paid the share of its level that the effort is worth`() {
    val id = master(level = 40)

    val kept = service().award(world, id, effortSeconds = 1920.0)

    assertEquals(kept, gained(id))
    assertEquals(true, abs(kept - 9504) <= 1) { "paid $kept" }
  }

  @Test
  fun `two awards in a row add up`() {
    val id = master(level = 40)
    val sut = service()

    val first = sut.award(world, id, 100.0)
    val second = sut.award(world, id, 100.0)

    assertEquals(first + second, gained(id))
  }

  @Test
  fun `effort beyond what the bank holds is not paid`() {
    val id = master(level = 40)
    val sut = service()

    val first = sut.award(world, id, 1920.0)
    val second = sut.award(world, id, 1920.0)
    val third = sut.award(world, id, 1920.0)

    // The bank holds an hour: the first fills 1920 s of it, the second gets the remaining 1680 s, the third none.
    assertEquals(true, second < first && second > 0)
    assertEquals(0, third)
  }

  @Test
  fun `a bestia working for the master who plays earns it for the master`() {
    val master = master(level = 40)
    val bestia = bestia()
    every { sessions.isPlaying(ACCOUNT_ID, MASTER_ID) } returns true
    every { sessions.getSelectedMasterEntityId(ACCOUNT_ID) } returns master

    val kept = service().award(world, bestia, 1920.0)

    assertEquals(kept, gained(master))
    assertNull(gained(bestia))
  }

  @Test
  fun `a bestia whose master is away earns nothing`() {
    val bestia = bestia()
    every { sessions.isPlaying(any(), any()) } returns false

    assertEquals(0, service().award(world, bestia, 1920.0))
  }

  @Test
  fun `something that is neither a master nor a bestia earns nothing`() {
    val npc = world.createEntity { id -> add(id, Level(40)) }

    assertEquals(0, service().award(world, npc, 1920.0))
  }

  @Test
  fun `the guild's levy is taken before the master keeps it`() {
    val id = master(level = 40)
    val halving = object : KillExpLevy {
      override val reads: ComponentClassSet = emptySet()
      override fun levy(world: World, masterEntityId: EntityId, amount: Int): Int = amount / 2
    }

    val kept = service(halving).award(world, id, 1920.0)

    assertEquals(true, abs(kept - 9504 / 2) <= 1) { "kept $kept" }
    assertEquals(kept, gained(id))
  }

  @Test
  fun `a master with no level yet earns nothing`() {
    val id = world.createEntity { id -> add(id, Master(masterId = MASTER_ID, name = "Smith")) }

    assertEquals(0, service().award(world, id, 1920.0))
  }

  private companion object {
    const val MASTER_ID = 42L
    const val ACCOUNT_ID = 7L
  }
}
