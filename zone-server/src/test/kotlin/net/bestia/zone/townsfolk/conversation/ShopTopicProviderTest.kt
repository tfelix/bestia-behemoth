package net.bestia.zone.townsfolk.conversation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.economy.shop.Counter
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter
import net.bestia.zone.economy.shop.ShopHours
import net.bestia.zone.townsfolk.domain.Occupation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopTopicProviderTest {

  private val merchants = mockk<MerchantStock>()
  private val shops = mockk<ShopCounter>()
  private val sut = ShopTopicProvider(merchants, shops, ConversationLineCatalogue().apply { load() })

  private val speaker = Speaker(
    entityId = MERCHANT,
    identity = 42L,
    settlement = 3,
    household = 4,
    name = "Mira",
    occupation = Occupation("shopkeeper", "shopkeeper", "general_store", HourWindow(7, 19), HourWindow(22, 6)),
    business = "general_store",
    member = Member(40, Kinship.HEAD),
    seed = 7L,
    dayOffsetMinutes = 20,
  )
  private val counter = Counter(MERCHANT, "Mira", setOf("bread"), ShopHours(HourWindow(7, 19), 20))

  init {
    every { merchants.counterOf(MERCHANT, any()) } returns counter
  }

  @Test
  fun `a merchant at work offers to trade`() {
    every { shops.isOpen(counter) } returns true

    val option = sut.rootOptions(speaker).single()

    assertEquals(Topics.SHOP + 1, option.topicId)
    assertEquals(OptionKind.ACTION, option.kind)
  }

  @Test
  fun `trading from the conversation opens the counter`() {
    every { shops.open(ACCOUNT, PLAYER, counter) } returns null

    val node = sut.nodeFor(Asker(ACCOUNT, PLAYER), speaker, Topics.SHOP + 1)

    verify { shops.open(ACCOUNT, PLAYER, counter) }
    assertTrue(node!!.speech.key.startsWith(ConversationKeys.SHOP_OPENED))
  }

  @Test
  fun `a merchant off shift tells you when they open, to the nearest hour`() {
    every { shops.isOpen(counter) } returns false

    val option = sut.rootOptions(speaker).single()
    val node = sut.nodeFor(Asker(ACCOUNT, PLAYER), speaker, option.topicId)!!

    assertEquals(OptionKind.TALK, option.kind)
    assertTrue(node.speech.key.startsWith(ConversationKeys.SHOP_HOURS))
    assertEquals(DialogArg.Number(7), node.speech.args[ConversationKeys.SLOT_OPENS])
    assertEquals(DialogArg.Number(19), node.speech.args[ConversationKeys.SLOT_CLOSES])
  }

  @Test
  fun `a shift that ends before the click answers with the hours`() {
    every { shops.open(ACCOUNT, PLAYER, counter) } returns OpError.SHOP_CLOSED

    val node = sut.nodeFor(Asker(ACCOUNT, PLAYER), speaker, Topics.SHOP + 1)

    assertTrue(node!!.speech.key.startsWith(ConversationKeys.SHOP_HOURS))
  }

  @Test
  fun `somebody who keeps no shop offers nothing`() {
    every { merchants.counterOf(MERCHANT, any()) } returns null

    assertTrue(sut.rootOptions(speaker).isEmpty())
  }

  private companion object {
    const val MERCHANT = 11L
    const val PLAYER = 22L
    const val ACCOUNT = 33L
  }
}
