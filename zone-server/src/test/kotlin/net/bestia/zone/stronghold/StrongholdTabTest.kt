package net.bestia.zone.stronghold

import net.bestia.zone.economy.CoinPayments
import net.bestia.zone.entity.GameMetrics.CoinSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrongholdTabTest {

  private val world = testWorld()
  private val contents = Inventory(mutableListOf())
  private var reserveBalance = 1_000_000.0
  private val reserve = mockk<WorldReserve>(relaxed = true) { every { available() } answers { reserveBalance } }
  private val jobs = mockk<AsyncJobExecutor>(relaxed = true)
  private var slots = 100
  private var held = listOf<Stronghold>()
  private val coins = mockk<CoinPayments> { every { pay(any(), any(), any(), any(), any()) } answers { arg<Long>(3) <= 40_000 } }
  private val mail = mockk<GuildStorageMail>(relaxed = true)

  private var stronghold = Stronghold(RANK, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH).copy(
    drops = listOf(
      TreasureDrop(Instant.EPOCH, coins = 60_000, goods = mapOf(IRON_ORE to 10)),
      TreasureDrop(Instant.EPOCH, coins = 60_000, goods = mapOf(IRON_ORE to 10, DRAKE_HIDE to 1)),
    )
  )

  private val tab = StrongholdTab(
    strongholds = mockk(relaxed = true) {
      every { on(RANK) } answers { stronghold }
      every { put(any()) } answers { stronghold = firstArg() }
      every { heldBy(GUILD) } answers { held }
    },
    seeds = mockk(relaxed = true),
    empyrites = mockk(relaxed = true),
    watchers = mockk(relaxed = true),
    treasury = mockk(relaxed = true),
    storages = mockk {
      every { contentsOf(GUILD) } returns contents
      every { slotsOf(any()) } answers { slots }
    },
    inventoryService = mockk(relaxed = true),
    itemTemplates = mockk(relaxed = true),
    commodities = mockk { every { coinItemId() } returns COIN },
    reserve = reserve,
    asyncJobExecutor = jobs,
    coins = coins,
    mail = mail,
    worldView = mockk(relaxed = true),
    messages = mockk(relaxed = true),
    config = StrongholdConfig(),
  )

  @Test
  fun `the leader takes every waiting drop into the guild storage, coins from the reserve`() {
    assertNull(tab.collect(world, master(leader = true), RANK))

    assertEquals(mapOf(COIN to 120_000, IRON_ORE to 20, DRAKE_HIDE to 1), contents.getItems().associate { it.itemId to it.amount })
    assertTrue(stronghold.drops.isEmpty())
    verify { reserve.charge(120_000.0, CoinSource.STRONGHOLD) }
    verify { jobs.submit(ItemHolder.GuildStorage(GUILD), any()) }
  }

  @Test
  fun `the reserve pays only what it holds`() {
    reserveBalance = 50_000.0

    tab.collect(world, master(leader = true), RANK)

    assertEquals(50_000, contents.getItems().single { it.itemId == COIN }.amount)
  }

  @Test
  fun `only the leader collects, with Guild Storage learned and room for all of it`() {
    assertEquals(OpError.REQUEST_REFUSED, tab.collect(world, master(leader = false), RANK))
    assertEquals(OpError.GUILD_STORAGE_NOT_LEARNED, tab.collect(world, master(leader = true, storage = false), RANK))

    slots = 2
    assertEquals(OpError.STORAGE_FULL, tab.collect(world, master(leader = true), RANK))
    assertEquals(2, stronghold.drops.size)
    assertTrue(contents.getItems().isEmpty())
  }

  @Test
  fun `the leader of a guild with no steward left pays per slot to have the sealed storage posted`() {
    contents.addItems(listOf(Inventory.Item(IRON_ORE, 20), Inventory.Item(DRAKE_HIDE, 1)))
    val leader = master(leader = true)

    held = listOf(stronghold.copy(hasSteward = true))
    assertEquals(OpError.REQUEST_REFUSED, tab.sendStorageAway(world, leader), "a steward opens it instead")

    held = emptyList()
    assertEquals(OpError.REQUEST_REFUSED, tab.sendStorageAway(world, master(leader = false)))
    assertNull(tab.sendStorageAway(world, leader))

    verify { coins.pay(world, leader, 5L, 2_000, CoinSource.STRONGHOLD) }
    assertTrue(contents.isEmpty())
    verify { jobs.submit(ItemHolder.GuildStorage(GUILD), any()) }
  }

  @Test
  fun `a leader who cannot pay keeps the storage sealed and full`() {
    contents.addItems((1..50L).map { Inventory.Item(100 + it, 1) })

    assertEquals(OpError.STRONGHOLD_CANNOT_AFFORD_SEND_AWAY, tab.sendStorageAway(world, master(leader = true)))
    assertEquals(50, contents.size())
  }

  private fun master(leader: Boolean, storage: Boolean = true): EntityId {
    val skills = if (storage) mapOf(SkillId.GUILD_STORAGE.name to 1) else emptyMap()

    return world.createEntity { id ->
      add(id, Master(5L))
      add(id, Account(7L))
      add(id, GuildMembership(GUILD, taxPercent = 5, isLeader = leader, skillLevels = skills))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val RANK = 2
    const val COIN = 3L
    const val IRON_ORE = 20L
    const val DRAKE_HIDE = 30L
  }
}
