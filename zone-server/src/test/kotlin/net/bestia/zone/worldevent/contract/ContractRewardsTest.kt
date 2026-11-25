package net.bestia.zone.worldevent.contract

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorAct
import net.bestia.zone.honor.HonorService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.EventLoot
import net.bestia.zone.worldevent.WorldEventEntry
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContractRewardsTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }
  private val ledger = ContributionLedger()
  private val honor = mockk<HonorService>(relaxed = true) { every { localAmountOf(HonorAct.DEFEND_SETTLEMENT) } returns 10 }
  private val loot = mockk<LootItemEntitySpawner>(relaxed = true)
  private val items = mockk<ItemTemplateRegistry> { every { idOf("mana_crystal") } returns 6L }
  private val worldService = mockk<WorldService>(relaxed = true)

  private val sut = ContractRewards(ledger, items, loot, honor, worldService, view)
  private val key = ContributionLedger.Key("Rift", 9)
  private val entry = WorldEventEntry(id = "rift", script = "Rift", cost = 3.0, loot = listOf(EventLoot("mana_crystal", 30)))
  private val instance = WorldEventInstance(eventId = "rift", x = 0, y = 0)

  private val ada = world.createEntity { id ->
    add(id, Master(masterId = 1, name = "Ada"))
    add(id, Position.fromVec3(Vec3L(10, 20, 0)))
    add(id, Account(accountId = 100))
  }

  @Test
  fun `the online contributor gets her share of the loot at her feet, and honor`() {
    ledger.add(key, 1, "Ada", ContributionLedger.Axis.FIGHTING, 75.0)
    ledger.add(key, 2, "Bo", ContributionLedger.Axis.HOLDING, 25.0)

    sut.grant(key, entry, instance)

    verify { loot.spawnLootItem(world, 6L, 23, Vec3L(10, 20, 0), lootOwner = 100L) }
    verify { honor.rewardGlobal(world, HonorAct.DEFEND_SETTLEMENT, ada) }
    assertTrue(ledger.sharesOf(key).isEmpty(), "paid out once")
  }

  @Test
  fun `someone who barely passed by gets nothing`() {
    ledger.add(key, 1, "Ada", ContributionLedger.Axis.HOLDING, 1.0)
    ledger.add(key, 2, "Bo", ContributionLedger.Axis.FIGHTING, 99.0)

    sut.grant(key, entry, instance)

    verify(exactly = 0) { loot.spawnLootItem(any(), any(), any(), any(), lootOwner = 100L) }
  }
}
