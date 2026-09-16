package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.dialog.DialogArg
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the fixed lines shipped with a count actually vary, and vary per person.
 *
 * The failure worth a test is the quiet one: a count of four with a picker that always returns the
 * same index looks exactly like variety in the yml and reads as a town of one voice in play. Both
 * halves matter - a whole town saying the same goodbye, and one person saying a different one each
 * time you ask - and only the first of those is visible in a file.
 */
class ConversationLineCatalogueTest {

  private val catalogue = ConversationLineCatalogue().also { it.load() }

  @Test
  fun `every key the conversation can say has a count`() {
    // The boot check inside load() is the real assertion; this names it so a failure reads as the
    // missing entry it is rather than as an unrelated test blowing up in its constructor.
    for (key in ConversationKeys.VARIED) {
      assertTrue(
        catalogue.lineFor(speaker(seed = 1L), key).key.startsWith(key + "_"),
        "$key does not resolve to a numbered phrasing"
      )
    }
  }

  @Test
  fun `asking one person twice gets the same words`() {
    val once = catalogue.lineFor(speaker(seed = 12L), ConversationKeys.GOODBYE)
    val again = catalogue.lineFor(speaker(seed = 12L), ConversationKeys.GOODBYE)

    assertEquals(once.key, again.key)
  }

  @Test
  fun `a town does not sign off in one voice`() {
    val used = (1L..200L).map { catalogue.lineFor(speaker(seed = it), ConversationKeys.GOODBYE).key }.toSet()

    assertTrue(used.size > 1, "two hundred people all said $used")
  }

  /**
   * And that one person's goodbye is not their answer about the town wearing a different name.
   *
   * The reason [ConversationVariants] takes a topic at all: drawing both off the speaker's seed alone
   * would tie them together, so everybody using the second goodbye would also use the second answer -
   * variety that collapses the moment a player talks to two people.
   */
  @Test
  fun `two lines from one speaker are drawn separately`() {
    val disagrees = (1L..200L).count {
      val who = speaker(seed = it)

      variantOf(catalogue.lineFor(who, ConversationKeys.GOODBYE).key) !=
        variantOf(catalogue.lineFor(who, ConversationKeys.NEWS_NONE).key)
    }

    assertTrue(disagrees > 0, "every one of two hundred people picked the same number for both lines")
  }

  @Test
  fun `the arguments a provider sends are carried through`() {
    val line = catalogue.lineFor(
      speaker(seed = 3L),
      ConversationKeys.ABOUT_TOWN,
      mapOf(ConversationKeys.SLOT_TRADE to DialogArg.Token("TRADE_BAKER")),
    )

    assertEquals(setOf(ConversationKeys.SLOT_TRADE), line.args.keys)
  }

  private fun variantOf(key: String): Int {
    return key.substringAfterLast('_').toInt()
  }

  private fun speaker(seed: Long): Speaker {
    return Speaker(
      entityId = seed,
      identity = seed,
      settlement = 3,
      household = 4,
      name = "Alden",
      occupation = Occupation("labourer", "labourer", null, HourWindow(6, 18), HourWindow(22, 6)),
      business = "baker",
      member = Member(40, Kinship.HEAD),
      seed = seed,
    )
  }
}
