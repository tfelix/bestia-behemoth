package net.bestia.zone.townsfolk.conversation

import net.bestia.zone.townsfolk.knowledge.Certainty
import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.knowledge.KnowledgeService
import net.bestia.zone.townsfolk.knowledge.TownKnowledge
import net.bestia.zone.dialog.DialogArg
import org.springframework.stereotype.Component

/**
 * What this person remembers, out of everything their town does.
 *
 * The delivery half of the knowledge model, and the reason it was built: `KnowledgeService` decides who
 * holds a memory, and this decides which of the ones they hold they happen to bring up today.
 *
 * ### Why the offer rotates by day and not by request
 *
 * Rerolling on every open would be easier and would be wrong. Asking the same person the same thing
 * twice and getting a different answer does not read as variety, it reads as a bug - and it lets a
 * player reroll a village until it offers the topic they wanted, which turns a conversation into a slot
 * machine. A day is long enough to be a fact about a person and short enough to be worth coming back for.
 */
@Component
class KnowledgeTopicProvider(
  private val knowledge: KnowledgeService,
  private val lines: ConversationLineCatalogue,
) : DialogTopicProvider {

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.KNOWLEDGE, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val held = knowledge.knownBy(speaker.identity)
    if (held.isEmpty()) {
      return emptyList()
    }

    return listOf(
      ConversationOption(Topics.KNOWLEDGE + NEWS, Line(ConversationKeys.NEWS_ASK))
    )
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    val local = Topics.localOf(Topics.KNOWLEDGE, topicId)
    if (local == NEWS) {
      return newsMenu(speaker)
    }

    val memory = knowledge.knownBy(speaker.identity).firstOrNull { it.topic == local } ?: return null

    return ConversationNode(lineOf(speaker, memory), listOf(back()))
  }

  /**
   * The handful of things this person would raise today.
   *
   * Mostly what not everybody knows, so the person who holds a rare memory or a legend actually says
   * it: a town shares some thirty famous memories, and by importance alone every person offered the
   * same four of them. One slot stays for the biggest famous thing, because everybody talks about
   * that. Trimmed - a list of forty memories is a database query rather than a conversation.
   */
  private fun newsMenu(speaker: Speaker): ConversationNode {
    val held = knowledge.knownBy(speaker.identity)
    if (held.isEmpty()) {
      return ConversationNode(lines.lineFor(speaker, ConversationKeys.NEWS_NONE), listOf(back()))
    }

    val (common, uncommon) = held
      .sortedWith(compareByDescending<Knowledge> { it.importance }.thenBy { it.topic })
      .partition { it.importance >= TownKnowledge.FAMOUS }
    val picked = uncommon.take(OFFERED - COMMON_SLOTS) + common.take(COMMON_SLOTS)
    val offered = picked + (uncommon + common).filterNot { it in picked }.take(OFFERED - picked.size)

    return ConversationNode(
      Line(ConversationKeys.NEWS_ASK),
      offered.map { ConversationOption(Topics.KNOWLEDGE + it.topic, askFor(it)) } + back()
    )
  }

  /**
   * The option label for a memory, as the memory's own key with `_ASK` on it.
   *
   * Derived rather than authored so that a new event kind cannot arrive with a line to say and no way to
   * ask about it - the boot check reads the same two keys per kind.
   */
  private fun askFor(memory: Knowledge): Line {
    return Line(memory.key + ASK_SUFFIX, memory.slots.mapValues { (_, slot) -> slot.toDialogArg() })
  }

  /**
   * A memory, in one of the phrasings its kind has.
   *
   * Keyed on the speaker as well as the event, so two people in one town tell you the same thing
   * differently - which costs nothing, because the rows have to exist anyway for the kind.
   */
  private fun lineOf(speaker: Speaker, memory: Knowledge): Line {
    val index = ConversationVariants.of(speaker, memory.topic.toLong(), memory.variants)
    val tale = Line("${memory.key}_$index", memory.slots.mapValues { (_, slot) -> slot.toDialogArg() })
    val frame = FRAMES[memory.certainty] ?: return tale

    // The tale's own arguments ride along, so the client can fill the nested line from them.
    return lines.lineFor(speaker, frame, tale.args + (ConversationKeys.SLOT_TALE to DialogArg.Token(tale.key)))
  }

  private fun back(): ConversationOption {
    return ConversationOption(Topics.KNOWLEDGE + NEWS, Line(ConversationKeys.BACK), OptionKind.BACK)
  }

  /**
   * A memory's slot as a wire argument.
   *
   * Two types that look alike and mean different things: a memory's slot is what the *world* can supply,
   * and a dialog argument is what the *client* can render. Most of the mapping is the identity, and the
   * day one of them grows a case the other cannot express, this is where it will be obvious.
   */
  private fun Knowledge.Slot.toDialogArg(): DialogArg {
    return when (this) {
      is Knowledge.Slot.Name -> DialogArg.Name(value)
      is Knowledge.Slot.Token -> DialogArg.Token(key)
      is Knowledge.Slot.Number -> DialogArg.Number(value)
    }
  }

  private companion object {

    /**
     * The menu's own node, at the top of the range so that no memory can share its id.
     *
     * A memory's topic is its own id and chronicle event ids start at 0, so a menu at 0 would answer
     * to the same topic as the oldest event in the world: asking about that event would silently reopen
     * the list. Memories keep `Topics.KNOWLEDGE + topic` exactly - see `Rumour.TOPIC_BASE`.
     */
    const val NEWS = Topics.STRIDE - 1

    /** Enough to feel like a person with things on their mind, few enough to read at a glance. */
    const val OFFERED = 4

    const val ASK_SUFFIX = "_ASK"

    /** How many of the offered memories may be ones the whole town knows. */
    const val COMMON_SLOTS = 1

    val FRAMES = mapOf(
      Certainty.TOLD to ConversationKeys.HEARSAY_TOLD,
      Certainty.LEGEND to ConversationKeys.HEARSAY_LEGEND,
    )
  }
}
