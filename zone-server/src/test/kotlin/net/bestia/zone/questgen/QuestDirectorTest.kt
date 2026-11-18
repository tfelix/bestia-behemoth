package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.questgen.offer.QuestDirector
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlan.State
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.questgen.seed.QuestSeedSource
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QuestDirectorTest {

  private val stored = ArrayList<StoredQuestPlan>()

  private val plans = mockk<StoredQuestPlanRepository> {
    every { findAllByState(any()) } answers { stored.filter { it.state == firstArg<State>() } }
    every { findAllByStateIn(any()) } answers { stored.filter { it.state in firstArg<Collection<State>>() } }
  }

  // Nothing known about any town, so each has room for the one quest every town may offer.
  private val sites = mockk<SettlementSiteIndex> { every { siteOf(any()) } returns null }
  private val offers = mockk<QuestOffers>(relaxed = true)
  private val seeds = object : QuestSeedSource {
    override fun seeds(): List<QuestSeed> {
      return stored.map { it.plan.seed }.filterNot { it.subject.index == GONE }
    }
  }

  private val director = QuestDirector(plans, listOf(seeds), sites, offers, QuestgenConfig())

  @Test
  fun `a town offers its best plan and keeps the rest for later`() {
    val good = planned(artifact = 1, giverIn(HALE, household = 1), score = 0.9)
    val better = planned(artifact = 2, giverIn(HALE, household = 2), score = 1.4)
    val elsewhere = planned(artifact = 3, giverIn(DUNMERE, household = 1), score = 0.2)

    director.run()

    assertEquals(listOf(State.PLANNED, State.OFFERED, State.OFFERED), listOf(good, better, elsewhere).map { it.state })
    verify { offers.refresh() }

    director.run()
    assertEquals(State.PLANNED, good.state, "the town's one place is still taken")
  }

  @Test
  fun `a plan whose cause is gone is withdrawn before anybody takes it`() {
    val found = planned(artifact = GONE, giverIn(HALE, household = 1), score = 2.0)
    val open = planned(artifact = 2, giverIn(HALE, household = 2), score = 1.0)

    director.run()

    assertEquals(State.ENDED, found.state)
    assertEquals(State.OFFERED, open.state)
  }

  private fun planned(artifact: Int, giver: WorldTarget, score: Double): StoredQuestPlan {
    return StoredQuestPlan(lostRelicPlan(artifact, giver, score)).also { stored += it }
  }

  private fun giverIn(town: Int, household: Int): WorldTarget {
    return WorldTarget.townsfolk(TownsfolkIdentity.of(town, household, 0))
  }

  private companion object {
    const val HALE = 3
    const val DUNMERE = 4
    const val GONE = 99
  }
}
