package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.core.GenRng
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.ai.knowledge.KnowledgeService
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

  override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
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
   * Ordered by importance so the town's own disasters come before a treaty signed two provinces away,
   * then trimmed - a list of forty memories is a database query rather than a conversation.
   */
  private fun newsMenu(speaker: Speaker): ConversationNode {
    val held = knowledge.knownBy(speaker.identity)
    if (held.isEmpty()) {
      return ConversationNode(Line(ConversationKeys.NEWS_NONE), listOf(back()))
    }

    val offered = held
      .sortedWith(compareByDescending<Knowledge> { it.importance }.thenBy { it.topic })
      .take(OFFERED)

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
    val variants = memory.variants
    val roll = GenRng.hashUnit(speaker.seed, memory.topic.toLong(), VARIANT_SALT)
    val index = (roll * variants).toInt().coerceIn(0, variants - 1) + 1

    return Line("${memory.key}_$index", memory.slots.mapValues { (_, slot) -> slot.toDialogArg() })
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
    const val NEWS = 0

    /** Enough to feel like a person with things on their mind, few enough to read at a glance. */
    const val OFFERED = 4

    const val ASK_SUFFIX = "_ASK"
    const val VARIANT_SALT = 0x7A15L
  }
}
