package net.bestia.zone.townsfolk.conversation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.economy.shop.Counter
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter
import net.bestia.zone.economy.shop.ShopHours
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.townsfolk.domain.Occupation
import kotlin.test.Test

/** Talking to a merchant at work is asking for their counter; anybody else gets a conversation. */
class TalkServiceTest {

  private val world = testWorld()
  private val speakers = mockk<SpeakerResolver>()
  private val conversation = mockk<ConversationService>()
  private val merchants = mockk<MerchantStock>()
  private val shops = mockk<ShopCounter>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = TalkService(speakers, conversation, merchants, shops, worldViewOf(world), out)

  private val player = world.createEntity { id -> add(id, Position(0, 0, 0)) }
  private val merchant = world.createEntity { id -> add(id, Position(2, 0, 0)) }
  private val speaker = Speaker(
    entityId = merchant,
    identity = 42L,
    settlement = 3,
    household = 4,
    name = "Mira",
    occupation = Occupation("shopkeeper", "shopkeeper", "general_store", HourWindow(7, 19), HourWindow(22, 6)),
    business = "general_store",
    member = Member(40, Kinship.HEAD),
    seed = 7L,
  )
  private val counter = Counter(merchant, "Mira", setOf("bread"), ShopHours.ALWAYS_OPEN)
  private val greeting = ConversationNode(Line("TALK_GREETING_SHOPKEEPER_1"), emptyList())

  init {
    every { speakers.of(merchant) } returns speaker
    every { merchants.counterOf(merchant, any()) } returns counter
    every { conversation.open(speaker) } returns greeting
    every { conversation.nodeFor(any(), speaker, Topics.ROOT) } returns greeting
  }

  @Test
  fun `a merchant at work opens their counter instead of a conversation`() {
    every { shops.isOpen(counter) } returns true
    every { shops.open(ACCOUNT, player, counter) } returns null

    sut.open(ACCOUNT, player, merchant)

    verify { shops.open(ACCOUNT, player, counter) }
    verify(exactly = 0) { out.sendToPlayer(ACCOUNT, ConversationSMSG(merchant, "Mira", greeting)) }
  }

  @Test
  fun `a merchant off shift is talked to`() {
    every { shops.isOpen(counter) } returns false

    sut.open(ACCOUNT, player, merchant)

    verify(exactly = 0) { shops.open(any(), any(), any()) }
    verify { out.sendToPlayer(ACCOUNT, ConversationSMSG(merchant, "Mira", greeting)) }
  }

  @Test
  fun `asking for the conversation root talks even to a merchant at work`() {
    every { shops.isOpen(counter) } returns true

    sut.choose(ACCOUNT, player, merchant, Topics.ROOT)

    verify(exactly = 0) { shops.open(any(), any(), any()) }
    verify { out.sendToPlayer(ACCOUNT, ConversationSMSG(merchant, "Mira", greeting)) }
  }

  private fun worldViewOf(world: World): WorldView = mockk<WorldView>().also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
  }

  private companion object {
    const val ACCOUNT = 33L
  }
}
