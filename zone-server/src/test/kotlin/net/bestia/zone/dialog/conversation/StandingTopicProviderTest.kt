package net.bestia.zone.dialog.conversation

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a townsperson says their trade is.
 *
 * The generator builds thirty trades and zone-server has six occupations to put people into, so the
 * occupation alone cannot answer: a baker, a mason and a tanner are one `labourer` between them, and all
 * three used to say "I keep the work here". The household's own trade is what makes them different.
 */
class StandingTopicProviderTest {

  private val worldService = mockk<WorldService>()

  // The real one, off the real dialogue.yml: what a phrasing count is worth is that the key it builds
  // exists, and a mock would assert that against itself.
  private val sut = StandingTopicProvider(worldService, ConversationLineCatalogue().apply { load() })

  init {
    // The town's name comes from the chronicle and is not what is under test here; an empty one keeps
    // the assertions about the trade token alone.
    val generated = mockk<GeneratedWorld>(relaxed = true)
    every { worldService.generated } returns generated
    every { generated.world.chronicle } returns mockk<Chronicle>(relaxed = true)
  }

  @Test
  fun `somebody who keeps a trade names that trade, not their occupation`() {
    assertEquals("TRADE_BAKER", tradeTokenOf(business = "baker", occupation = "labourer"))
    assertEquals("TRADE_TANNER", tradeTokenOf(business = "tanner", occupation = "labourer"))
  }

  @Test
  fun `somebody who keeps none falls back to their occupation`() {
    assertEquals("TRADE_FARMER", tradeTokenOf(business = null, occupation = "farmer"))
    assertEquals("TRADE_CHILD", tradeTokenOf(business = null, occupation = "child"))
  }

  @Test
  fun `the answer is one of the phrasings the client carries`() {
    val node = sut.nodeFor(Asker(accountId = 1L, entityId = 1L), speaker(business = "baker", occupation = "labourer"), Topics.STANDING + 1)

    assertTrue(
      node!!.speech.key.matches(Regex(ConversationKeys.ABOUT_TOWN + "_[1-9]")),
      "expected a numbered phrasing of ${ConversationKeys.ABOUT_TOWN}, was ${node.speech.key}"
    )
  }

  private fun tradeTokenOf(business: String?, occupation: String): String {
    val node = sut.nodeFor(Asker(accountId = 1L, entityId = 1L), speaker(business, occupation), Topics.STANDING + 1)
    val arg = node!!.speech.args.getValue(ConversationKeys.SLOT_TRADE)

    return (arg as DialogArg.Token).key
  }

  private fun speaker(business: String?, occupation: String): Speaker {
    return Speaker(
      entityId = 1L,
      identity = 42L,
      settlement = 3,
      household = 4,
      name = "Alden",
      occupation = Occupation(occupation, occupation, null, HourWindow(6, 18), HourWindow(22, 6)),
      business = business,
      member = Member(40, Kinship.HEAD),
      seed = 7L,
    )
  }
}
