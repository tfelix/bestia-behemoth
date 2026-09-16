package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.core.GenRng
import net.bestia.zone.dialog.conversation.smalltalk.CircumstanceService
import net.bestia.zone.dialog.conversation.smalltalk.SmallTalk
import net.bestia.zone.dialog.conversation.smalltalk.SmallTalkCatalogue
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * The one mundane thing this person feels like mentioning today, if anything.
 *
 * Knowledge makes a town informative and does not make anybody a person. This is the counterweight, and
 * it is deliberately rationed: **at most one** topic, behind a per-day roll, competing for a sampled
 * root slot like everything else. A village where every second option is flavour is as tiring to read
 * as one where every line is a chronicle entry.
 *
 * Not pinned, so it displaces a memory only sometimes, and never displaces a shop.
 */
@Component
class SmallTalkTopicProvider(
  private val catalogue: SmallTalkCatalogue,
  private val circumstances: CircumstanceService,
  private val clock: BestiaClock,
) : DialogTopicProvider {

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.SMALL_TALK, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val line = todaysLine(speaker) ?: return emptyList()

    return listOf(ConversationOption(Topics.SMALL_TALK + catalogue.indexOf(line), Line(line.askKey)))
  }

  /**
   * Answered whenever the line still holds, rather than only when it is the one picked today.
   *
   * A click that arrives after midnight would otherwise be a dead option, and the thing that made it
   * stale is a clock rather than anything the player did. Re-checking the *gates* keeps the promise
   * that matters - nobody says a line that is not true of them - while letting a late click land.
   */
  override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
    val index = Topics.localOf(Topics.SMALL_TALK, topicId)
    val line = catalogue.at(index) ?: return null
    val circumstance = circumstances.of(speaker) ?: return null

    if (!line.holdsFor(circumstance)) {
      return null
    }

    return ConversationNode(Line(line.replyKey(variantFor(speaker, index, line))), emptyList())
  }

  /**
   * Which phrasing of the reply this person uses, off their own seed rather than the day's.
   *
   * Not the day's, because the line survives midnight on purpose - see [nodeFor] - and a reply that
   * changed wording between the click and the answer would undo the reason it does. Keyed on the
   * line's position so that somebody's barley is not drawn with the same number as their fence.
   */
  private fun variantFor(speaker: Speaker, index: Int, line: SmallTalk): Int {
    return ConversationVariants.of(speaker, ConversationVariants.SMALL_TALK + index, line.variants)
  }

  /**
   * Whether this person has small talk today and which, as two rolls off their own seed.
   *
   * Two rather than one so the knob means what it says: a single roll over an extended pool would make
   * the chance depend on how many lines a speaker happens to be eligible for, and adding a line about
   * the coast would quietly make every fisherman chattier.
   */
  private fun todaysLine(speaker: Speaker): SmallTalk? {
    val today = clock.now().absoluteDay.toLong()

    if (GenRng.hashUnit(speaker.seed, today, OFFER_SALT) >= catalogue.chance) {
      return null
    }

    val circumstance = circumstances.of(speaker) ?: return null
    val eligible = catalogue.eligible(circumstance)

    if (eligible.isEmpty()) {
      return null
    }

    val roll = GenRng.hashUnit(speaker.seed, today, PICK_SALT)

    return eligible[(roll * eligible.size).toInt().coerceAtMost(eligible.size - 1)]
  }

  private companion object {
    const val OFFER_SALT = 0x5A1CL
    const val PICK_SALT = 0x5A1DL
  }
}
