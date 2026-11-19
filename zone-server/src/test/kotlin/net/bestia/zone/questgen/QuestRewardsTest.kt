package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.honor.HonorService
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.questgen.ending.QuestRewards
import net.bestia.zone.questgen.ending.QuestRewards.Ending
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestRewardsTest {

  private val world = testWorld()
  private val master = world.createEntity { id -> add(id, Master(masterId = MASTER, name = "Ayla")) }

  // Runs what is posted at once, as the next tick would.
  private val view = mockk<WorldView> {
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(world) }
  }
  private val sessions = mockk<ConnectionInfoService> { every { activeMasters() } returns mapOf(ACCOUNT to MASTER) }
  private val masters = mockk<MasterEntityResolver> { every { selectedMasterEntityOf(ACCOUNT) } returns master }
  private val honor = mockk<HonorService>(relaxed = true)
  private val economy = mockk<SettlementEconomyService> { every { payReward(HOME, any(), any()) } returns 340 }
  private val coins = mockk<CommodityItems> { every { coinItemId() } returns COIN }
  private val config = QuestgenConfig()

  private val rewards = QuestRewards(view, sessions, honor, economy, coins, config, masters)
  private val plan = lostRelicPlan(artifact = 3, WorldTarget.townsfolk(1), home = HOME)

  @Test
  fun `bringing the artifact home pays experience, the town's honor and coins from its treasury`() {
    rewards.pay(MASTER, plan, Ending.RETURN)

    assertEquals(plan.suggestedLevel * config.expPerLevel, world.get(master, GainExp::class)!!.value)
    verify { honor.changeSettlement(MASTER, "Ayla", HOME, config.returnHonor) }
    val paid = world.get(master, CreateItemsIntent::class)!!.grants.single()
    assertEquals(COIN to 340, paid.itemId to paid.amount)
  }

  @Test
  fun `keeping it pays experience only, and its home town thinks less of the master`() {
    rewards.pay(MASTER, plan, Ending.KEEP)

    assertEquals(plan.suggestedLevel * config.expPerLevel, world.get(master, GainExp::class)!!.value)
    verify { honor.changeSettlement(MASTER, "Ayla", HOME, -config.keepHonorLoss) }
    assertNull(world.get(master, CreateItemsIntent::class))
  }

  @Test
  fun `a quest ends as a return only when its last step was the bring step`() {
    assertEquals(Ending.RETURN, Ending.of(plan, lastStage = 3))
    assertEquals(Ending.KEEP, Ending.of(plan, lastStage = 2))
    assertEquals(Ending.KEEP, Ending.of(lostRelicPlan(artifact = 3, WorldTarget.townsfolk(1)), lastStage = 1))
  }

  private companion object {
    const val ACCOUNT = 8L
    const val MASTER = 80L
    const val HOME = 6
    const val COIN = 1L
  }
}
