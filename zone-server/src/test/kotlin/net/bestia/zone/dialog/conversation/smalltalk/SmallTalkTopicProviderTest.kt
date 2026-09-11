package net.bestia.zone.dialog.conversation.smalltalk

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.dialog.conversation.SmallTalkTopicProvider
import net.bestia.zone.dialog.conversation.Speaker
import net.bestia.zone.dialog.conversation.Topics
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.Season
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That small talk stays rationed, stays the same all day, and differs between people.
 *
 * All three are the same property from different angles: the line is a pure function of the speaker and
 * the day. Lose it and an NPC changes their mind about the barley every time the window is reopened,
 * which reads worse than having no small talk at all.
 */
class SmallTalkTopicProviderTest {

  @Test
  fun `at most one topic ever reaches the root`() {
    val provider = provider()

    // Across many people rather than one: a farmer is eligible for a handful of lines at once, and the
    // cap has to come from the provider rather than from the pool happening to be thin.
    for (seed in 1L..200L) {
      assertTrue(provider.rootOptions(speaker(seed)).size <= 1, "speaker $seed was offered more than one")
    }
  }

  @Test
  fun `a person says the same thing all day and a different thing tomorrow`() {
    val today = provider(day = 5)
    val tomorrow = provider(day = 6)

    val chatty = (1L..200L).first { today.rootOptions(speaker(it)).isNotEmpty() }

    assertEquals(
      today.rootOptions(speaker(chatty)),
      today.rootOptions(speaker(chatty)),
      "the same speaker on the same day answered differently"
    )

    val changed = (1L..200L).count {
      today.rootOptions(speaker(it)).firstOrNull()?.topicId != tomorrow.rootOptions(speaker(it)).firstOrNull()?.topicId
    }

    // Two thirds of the town, measured. A low bar would pass on a single person changing their mind,
    // which is what a pick that had collapsed onto the seed alone would look like.
    assertTrue(changed > 50, "only $changed of two hundred people had anything new to say the next day")
  }

  @Test
  fun `two people of one trade in one town are not the same person`() {
    val provider = provider()
    val offered = (1L..200L).mapNotNull { provider.rootOptions(speaker(it)).firstOrNull()?.topicId }

    assertTrue(offered.toSet().size > 1, "every farmer in town said the identical thing")
  }

  /** The knob is the whole boredom control, so it has to actually silence people. */
  @Test
  fun `the chance decides how many people have anything to say`() {
    val silent = provider(chance = 0.0)
    val talkative = provider(chance = 1.0)

    val never = (1L..200L).count { silent.rootOptions(speaker(it)).isNotEmpty() }
    val always = (1L..200L).count { talkative.rootOptions(speaker(it)).isNotEmpty() }

    assertEquals(0, never)
    assertEquals(200, always)

    // And that the shipped setting lands near what it says, rather than the roll being ignored between
    // the two extremes - 97 of 200 when this was written, against a nominal 45 per cent.
    val shipped = provider().let { p -> (1L..200L).count { p.rootOptions(speaker(it)).isNotEmpty() } }
    assertTrue(shipped in 60..140, "$shipped of 200 spoke, which is nowhere near the configured chance")
  }

  @Test
  fun `a topic is answered only while the line is still true of the speaker`() {
    val provider = provider(chance = 1.0)
    val speaker = speaker(1L)
    val topicId = provider.rootOptions(speaker).first().topicId

    assertNotNull(provider.nodeFor(speaker, topicId))

    // The same click from someone the line was never about. Small talk that a guard can inherit from a
    // farmer is exactly the generic filler the gates exist to prevent.
    val elsewhere = provider(chance = 1.0, occupation = "guard")
    assertNull(elsewhere.nodeFor(speaker(1L, occupation = "guard"), topicId))
  }

  @Test
  fun `an unknown topic in range is refused rather than guessed at`() {
    assertNull(provider().nodeFor(speaker(1L), Topics.SMALL_TALK + 9_999))
  }

  private fun provider(
    day: Int = 1,
    chance: Double? = null,
    occupation: String = "farmer",
  ): SmallTalkTopicProvider {
    val catalogue = SmallTalkCatalogue(OccupationCatalogue().also { it.load() }).also { it.load() }

    val circumstances = mockk<CircumstanceService>()
    every { circumstances.of(any()) } answers {
      circumstanceOf(firstArg<Speaker>().occupation.id)
    }

    val clock = mockk<BestiaClock>()
    every { clock.now() } returns mockk(relaxed = true) { every { absoluteDay } returns day.toDouble() }

    return SmallTalkTopicProvider(overriding(catalogue, chance), circumstances, clock)
  }

  /**
   * The shipped catalogue with only its knob replaced.
   *
   * A hand-built pool would test a pool nobody ships; the two ends of the knob are what the tests
   * actually need to pin.
   */
  private fun overriding(catalogue: SmallTalkCatalogue, chance: Double?): SmallTalkCatalogue {
    if (chance == null) {
      return catalogue
    }

    val spy = mockk<SmallTalkCatalogue>()
    every { spy.chance } returns chance
    every { spy.eligible(any()) } answers { catalogue.eligible(firstArg()) }
    every { spy.at(any()) } answers { catalogue.at(firstArg()) }
    every { spy.indexOf(any()) } answers { catalogue.indexOf(firstArg()) }
    every { spy.all() } returns catalogue.all()

    return spy
  }

  private fun circumstanceOf(occupation: String): Circumstance {
    return Circumstance(
      occupation = occupation,
      age = 30,
      kinship = Kinship.HEAD,
      terrain = RegionKind.DOWNS,
      season = Season.SPRING,
      weather = WeatherKind.CLEAR,
      walled = false,
      sacked = false,
      wealth = 0.4,
    )
  }

  private fun speaker(seed: Long, occupation: String = "farmer"): Speaker {
    return Speaker(
      entityId = 1L,
      identity = 42L,
      settlement = 3,
      household = 4,
      name = "Alden",
      occupation = Occupation(occupation, occupation, null, HourWindow(6, 18), HourWindow(22, 6)),
      member = Member(30, Kinship.HEAD),
      seed = seed,
    )
  }
}
