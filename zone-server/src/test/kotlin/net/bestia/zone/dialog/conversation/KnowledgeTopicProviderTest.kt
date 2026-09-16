package net.bestia.zone.dialog.conversation

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.ai.knowledge.KnowledgeService
import net.bestia.zone.ai.knowledge.Locality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That every memory this person offers can actually be asked about.
 *
 * The provider owns two kinds of node in one id space - the list of things to ask, and the memories
 * themselves - and only the memories' ids are handed to it from outside. So the list's own id is the
 * one that can collide, and a collision is invisible from everywhere else: the option is offered, the
 * player clicks it, and the server answers with the list it came from.
 */
class KnowledgeTopicProviderTest {

  private val knowledge = mockk<KnowledgeService>()

  private val sut = KnowledgeTopicProvider(knowledge, ConversationLineCatalogue().apply { load() })

  @Test
  fun `the first event the chronicle ever logged is a memory and not the menu`() {
    holds(memory(topic = FIRST_EVENT))

    val node = assertNotNull(sut.nodeFor(speaker(), Topics.KNOWLEDGE + FIRST_EVENT))

    assertTrue(
      node.speech.key.startsWith(KEY),
      "asking about event $FIRST_EVENT answered with ${node.speech.key}"
    )
  }

  @Test
  fun `every option the menu offers answers with the memory it named`() {
    val held = listOf(memory(topic = FIRST_EVENT), memory(topic = 1), memory(topic = 50_000))
    holds(*held.toTypedArray())

    val menu = assertNotNull(sut.nodeFor(speaker(), menuTopic()))
    val asked = menu.options
      .filter { it.kind == OptionKind.TALK }
      .map { assertNotNull(sut.nodeFor(speaker(), it.topicId)).speech.key }

    assertEquals(held.size, asked.size, "menu offered ${menu.options} for ${held.size} memories")
    assertTrue(asked.all { it.startsWith(KEY) }, "one option answered with something else: $asked")
  }

  @Test
  fun `the menu sits clear of every id a memory is given`() {
    holds(memory(topic = FIRST_EVENT))

    val menu = Topics.localOf(Topics.KNOWLEDGE, menuTopic())
    val taken = (0..CHRONICLE_CEILING) + (RUMOURS..RUMOURS + RUMOUR_CEILING)

    assertTrue(menu !in taken, "the menu answers to $menu, which a memory can be given")
  }

  private fun holds(vararg held: Knowledge) {
    every { knowledge.knownBy(IDENTITY) } returns held.toList()
  }

  /** The id the provider itself offers, so the test does not have to know where the menu sits. */
  private fun menuTopic(): Int {
    return sut.rootOptions(speaker()).single().topicId
  }

  private fun memory(topic: Int): Knowledge {
    return Knowledge(
      topic = topic,
      key = KEY,
      slots = emptyMap(),
      importance = 50,
      year = 1,
      locality = Locality.OWN_TOWN,
      variants = 2,
    )
  }

  private fun speaker(): Speaker {
    return Speaker(
      entityId = 1L,
      identity = IDENTITY,
      settlement = 3,
      household = 4,
      name = "Otbert",
      occupation = Occupation("farmer", "farmer", null, HourWindow(6, 18), HourWindow(22, 6)),
      business = null,
      member = Member(40, Kinship.HEAD),
      seed = 7L,
    )
  }

  private companion object {
    const val IDENTITY = 42L

    /** `HistorySim` numbers events from zero, so this is a topic every world has. */
    const val FIRST_EVENT = 0

    const val KEY = "HISTORY_CIV_FOUNDED"

    /** A pruned thousand-year chronicle keeps a few thousand events, well short of this. */
    const val CHRONICLE_CEILING = 20_000

    /** `Rumour.TOPIC_BASE`, not imported: this asserts against the layout, not against the constant. */
    const val RUMOURS = 50_000

    const val RUMOUR_CEILING = 20_000
  }
}
