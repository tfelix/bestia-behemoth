package net.bestia.zone.dialog.conversation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.GenRng
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Builds the thing a townsperson says, and the things that may be said back.
 *
 * The only place a [ConversationNode] is assembled, on `DialogService`'s argument: a caller that had to
 * know which providers exist and how they are ordered would be a second copy of this, free to disagree.
 *
 * ### It keeps nothing
 *
 * There is no session, no cursor and no timeout, because an option id is a *topic* rather than a
 * position in a list. A node is a pure function of who is speaking, which topic, and the day - so a
 * reconnect costs nothing, a restart costs nothing, and a click that arrives late resolves to what it
 * would have resolved to anyway.
 */
@Service
class ConversationService(
  providers: List<DialogTopicProvider>,
  private val clock: BestiaClock,
) {

  private val pinned = providers.filter { it.pinned }.sortedBy { it.order }
  private val sampled = providers.filterNot { it.pinned }

  /** The root: a greeting and whatever this person has to offer today. */
  fun open(speaker: Speaker): ConversationNode {
    return ConversationNode(greeting(speaker), rootOptions(speaker))
  }

  /**
   * The node behind one option, or null if this speaker cannot talk about it.
   *
   * Null rather than an empty node: a topic that does not belong to this speaker is a stale click or a
   * hand-built message, and answering it with silence would be indistinguishable from a bug.
   */
  fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    if (topicId == Topics.ROOT) {
      return open(speaker)
    }

    val provider = (pinned + sampled).firstOrNull { it.owns(topicId) }
    if (provider == null) {
      LOG.debug { "No provider owns topic $topicId, asked of ${speaker.identity}" }
      return null
    }

    val node = provider.nodeFor(asker, speaker, topicId) ?: return null

    // Every leaf needs a way back to the root, or a player who asks one question is stuck with the
    // window open and nothing but the close button. Providers that already offer one keep theirs.
    if (node.options.isEmpty()) {
      return node.copy(options = listOf(rootOption()))
    }

    return node
  }

  /**
   * Pinned first in their own order, then a sample of the rest.
   *
   * Capped, because a wall of options is not a conversation. The pinned tier is never trimmed - it is
   * the part a player is meant to be able to rely on - so a speaker with a great many pinned options
   * simply has a long root, which is a catalogue problem rather than a runtime one.
   */
  private fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val fixed = pinned.flatMap { it.rootOptions(speaker) }
    val ending = fixed.filter { it.kind == OptionKind.END }
    val leading = fixed - ending.toSet()

    val optional = sampled
      .flatMap { it.rootOptions(speaker) }
      .sortedBy { GenRng.hashUnit(speaker.seed, today(), it.topicId.toLong(), SAMPLE_SALT) }
      .take((MAX_OPTIONS - fixed.size).coerceAtLeast(0))

    return leading + optional + ending
  }

  /**
   * Who you are talking to, by name.
   *
   * The name is a [DialogArg.Name] rather than part of the key: it is an invented word out of the
   * culture's own pool, so there is nothing in it to translate and everything around it still is.
   */
  private fun greeting(speaker: Speaker): Line {
    return Line(
      ConversationKeys.GREETING_PREFIX + speaker.occupation.id.uppercase() + "_" +
        ConversationVariants.of(speaker, ConversationVariants.GREETING, speaker.occupation.dialog.greetings),
      mapOf(ConversationKeys.SLOT_NAME to DialogArg.Name(speaker.name))
    )
  }

  private fun rootOption(): ConversationOption {
    return ConversationOption(Topics.ROOT, Line(ConversationKeys.BACK), OptionKind.BACK)
  }

  /**
   * The game day, which is what the offered topics are fixed against.
   *
   * Not the real clock: a player and a townsperson have to agree on what "today" is, and only one of
   * those two has a wristwatch.
   */
  private fun today(): Long {
    return clock.now().absoluteDay.toLong()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** As many as a player will actually read before picking one. */
    const val MAX_OPTIONS = 5

    const val SAMPLE_SALT = 0x5A11L
  }
}
