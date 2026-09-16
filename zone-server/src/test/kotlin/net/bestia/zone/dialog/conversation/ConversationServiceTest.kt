package net.bestia.zone.dialog.conversation

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.environment.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a root is assembled, and that a conversation needs nothing remembered between two requests.
 *
 * Built out of hand-written providers rather than the shipped ones, because what is under test is the
 * service's contract with any provider - pinned survives, optional competes, every leaf has a way back -
 * and pinning it to the four that happen to exist today would make it a change-detector.
 */
class ConversationServiceTest {

  @Test
  fun `pinned options survive a crowd of optional ones and keep their order`() {
    val service = ConversationService(
      listOf(
        provider(Topics.OCCUPATION, pinned = true, order = 0, offers = 2),
        provider(Topics.STANDING, pinned = true, order = 10, offers = 1),
        provider(Topics.KNOWLEDGE, pinned = false, offers = 20),
        farewell(),
      ),
      clock(day = 4),
    )

    val options = service.open(speaker()).options

    assertTrue(options.size <= 5, "a root of ${options.size} options is a list, not a conversation")
    assertEquals(
      listOf(Topics.OCCUPATION, Topics.OCCUPATION + 1, Topics.STANDING),
      options.take(3).map { it.topicId },
      "pinned options must lead, in their providers' order"
    )
    assertEquals(OptionKind.END, options.last().kind, "the way out must be last")
  }

  /**
   * The property that makes a stateless conversation believable.
   *
   * Asking the same person the same thing twice must give the same answer. Rerolling per request reads
   * as a bug rather than as variety, and it would let a player reroll a village until it offered the
   * topic they wanted.
   */
  @Test
  fun `the same speaker on the same day is offered the same topics`() {
    val service = ConversationService(
      listOf(provider(Topics.KNOWLEDGE, pinned = false, offers = 30), farewell()),
      clock(day = 9),
    )
    val speaker = speaker()

    assertEquals(
      service.open(speaker).options.map { it.topicId },
      service.open(speaker).options.map { it.topicId },
    )
  }

  @Test
  fun `a new day brings different things to talk about`() {
    val providers = { listOf(provider(Topics.KNOWLEDGE, pinned = false, offers = 30), farewell()) }
    val speaker = speaker()

    val monday = ConversationService(providers(), clock(day = 1)).open(speaker).options.map { it.topicId }
    val tuesday = ConversationService(providers(), clock(day = 2)).open(speaker).options.map { it.topicId }

    assertTrue(monday != tuesday, "a townsperson offered the same topics on two different days")
  }

  @Test
  fun `two people of one trade do not say the same things`() {
    val providers = { listOf(provider(Topics.KNOWLEDGE, pinned = false, offers = 30), farewell()) }

    val one = ConversationService(providers(), clock(day = 1)).open(speaker(seed = 1L)).options.map { it.topicId }
    val other = ConversationService(providers(), clock(day = 1)).open(speaker(seed = 2L)).options.map { it.topicId }

    assertTrue(one != other, "two villagers with different seeds were offered exactly the same topics")
  }

  /** A leaf with no way back leaves the player holding an open window and one button. */
  @Test
  fun `a leaf is given a way back to the root`() {
    val service = ConversationService(listOf(provider(Topics.STANDING, pinned = true, offers = 1)), clock())

    val node = service.nodeFor(speaker(), Topics.STANDING)

    assertNotNull(node)
    assertEquals(OptionKind.BACK, node.options.single().kind)
    assertEquals(Topics.ROOT, node.options.single().topicId)
  }

  @Test
  fun `the root topic reopens the conversation`() {
    val service = ConversationService(listOf(provider(Topics.STANDING, pinned = true, offers = 1)), clock())

    val node = service.nodeFor(speaker(), Topics.ROOT)

    assertNotNull(node)
    assertEquals(ConversationKeys.GREETING, node.speech.key)
  }

  /** A stale click or a hand-built message. Answering it with an empty node would look like a bug. */
  @Test
  fun `a topic nobody owns is refused rather than answered`() {
    val service = ConversationService(listOf(provider(Topics.STANDING, pinned = true, offers = 1)), clock())

    assertNull(service.nodeFor(speaker(), Topics.RUMOUR + 7))
  }

  private fun provider(base: Int, pinned: Boolean, order: Int = 0, offers: Int): DialogTopicProvider {
    val ownPinned = pinned
    val ownOrder = order

    return object : DialogTopicProvider {
      override val pinned = ownPinned
      override val order = ownOrder

      override fun owns(topicId: Int): Boolean {
        return Topics.owns(base, topicId)
      }

      override fun rootOptions(speaker: Speaker): List<ConversationOption> {
        return (0 until offers).map { ConversationOption(base + it, Line("ASK_$it")) }
      }

      override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
        return ConversationNode(Line("SAID_$topicId"), emptyList())
      }
    }
  }

  private fun farewell(): DialogTopicProvider {
    return object : DialogTopicProvider {
      override val pinned = true
      override val order = Int.MAX_VALUE

      override fun owns(topicId: Int): Boolean {
        return Topics.owns(Topics.FAREWELL, topicId)
      }

      override fun rootOptions(speaker: Speaker): List<ConversationOption> {
        return listOf(ConversationOption(Topics.FAREWELL, Line(ConversationKeys.FAREWELL), OptionKind.END))
      }

      override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode {
        return ConversationNode(Line(ConversationKeys.GOODBYE), emptyList())
      }
    }
  }

  private fun clock(day: Int = 1): BestiaClock {
    val clock = mockk<BestiaClock>()
    every { clock.now() } returns mockk(relaxed = true) { every { absoluteDay } returns day.toDouble() }

    return clock
  }

  private fun speaker(seed: Long = 7L): Speaker {
    return Speaker(
      entityId = 1L,
      identity = 42L,
      settlement = 3,
      household = 4,
      name = "Alden",
      occupation = Occupation("farmer", "farmer", null, HourWindow(6, 18), HourWindow(22, 6)),
      business = null,
      member = Member(40, Kinship.HEAD),
      seed = seed,
    )
  }
}
