package net.bestia.zone.auction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TradeAgreementServiceTest {

  private val world = testWorld()
  private val offers = TradeOffers()
  private val links = TradeLinks(mockk(relaxed = true), mockk(relaxed = true))
  private val houses = mockk<PlayerHouses> { every { ownedBy(any()) } returns listOf(mockk()) }
  private val skills = mockk<SkillRepository> { every { findByIdentifier("M_TRADE_AGREEMENT") } returns mockk { every { id } returns 59L } }
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val service = TradeAgreementService(offers, links, houses, skills, out).also { it.resolveSkills() }

  private val ada = TradeLinks.Partner(1L, "Ada")

  @Test
  fun `an accepted offer links both owners and tells both`() {
    val bo = spawnMaster(2L, "Bo", agreementLevel = 1)
    val offerId = offers.offer(ada, fromAccountId = 11L, toMasterId = 2L, fromLevel = 1)

    service.answer(world, bo, offerId, accept = true)

    assertTrue(links.isLinked(1L, 2L))
    verify { out.sendToPlayer(11L, TradeLinkNoticeSMSG(TradeLinkNoticeSMSG.Notice.LINKED, "Bo")) }
  }

  @Test
  fun `a master without Trade Agreement cannot be linked`() {
    val bo = spawnMaster(2L, "Bo", agreementLevel = 0)
    val offerId = offers.offer(ada, fromAccountId = 11L, toMasterId = 2L, fromLevel = 1)

    service.answer(world, bo, offerId, accept = true)

    assertFalse(links.isLinked(1L, 2L))
  }

  @Test
  fun `an offer to somebody else cannot be answered`() {
    val cy = spawnMaster(3L, "Cy", agreementLevel = 1)
    val offerId = offers.offer(ada, fromAccountId = 11L, toMasterId = 2L, fromLevel = 1)

    service.answer(world, cy, offerId, accept = true)

    assertFalse(links.isLinked(1L, 3L))
  }

  private fun spawnMaster(masterId: Long, name: String, agreementLevel: Int): Long {
    return world.createEntity { id ->
      add(id, Master(masterId, name))
      add(id, Account(masterId + 10))
      add(id, KnownSkills(mutableMapOf(59L to agreementLevel)))
    }
  }
}
