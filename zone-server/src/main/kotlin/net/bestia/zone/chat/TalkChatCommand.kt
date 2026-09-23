package net.bestia.zone.chat

import net.bestia.account.Authority
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.conversation.Asker
import net.bestia.zone.dialog.conversation.ConversationNode
import net.bestia.zone.dialog.conversation.ConversationService
import net.bestia.zone.dialog.conversation.Line
import net.bestia.zone.dialog.conversation.Speaker
import net.bestia.zone.dialog.conversation.SpeakerResolver
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Holds a conversation with the nearest townsperson, in the chat window.
 *
 * The client has no conversation panel yet, and waiting for one would mean the whole server half
 * shipping unexercised - the failure `SettlementLoreService` spent two releases documenting. This is the
 * same nodes and the same providers the real message carries, rendered as text: `/talk` opens, `/talk n`
 * picks an option.
 *
 * It prints **keys rather than sentences**, because there are no sentences. A line is a translation key
 * and its arguments, and the English lives in the client's translation file - so reading the key here is
 * also the better check, since it is what a phrasing has to be written against.
 */
@Component
class TalkChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val speakers: SpeakerResolver,
  private val conversation: ConversationService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun getHelpText(): String {
    return "/talk [OPTION] - Talks to the nearest townsperson, or picks one of the options they offered."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val actor = connectionInfoService.getActiveEntityId(playerId)
    val at = world.read { get(actor, Position::class) }?.toVec3L() ?: return false

    val nearest = nearestTownsperson(at.x, at.y)
    if (nearest == null) {
      reply(playerId, "Nobody within $RANGE of you is a townsperson. Stand in a village.")
      return true
    }

    val speaker = speakers.of(nearest)
    if (speaker == null) {
      reply(playerId, "Entity $nearest carries a Townsfolk component but resolves to nobody.")
      return true
    }

    val choice = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.toInt()
    val node = nodeFor(Asker(playerId, nearest), speaker, choice)
    if (node == null) {
      reply(playerId, "That option is not one of theirs. Run /talk to see what is.")
      return true
    }

    describe(speaker, node).forEach { reply(playerId, it) }

    return true
  }

  /**
   * The nth option of the root, rather than a raw topic id.
   *
   * A topic id is a five- or six-figure number a GM would have to read off a previous line and retype.
   * The real client sends the id; this is scaffolding, and being usable matters more than mirroring the
   * wire exactly.
   */
  private fun nodeFor(asker: Asker, speaker: Speaker, choice: Int?): ConversationNode? {
    if (choice == null) {
      return conversation.open(speaker)
    }

    val option = conversation.open(speaker).options.getOrNull(choice - 1) ?: return null

    return conversation.nodeFor(asker, speaker, option.topicId)
  }

  /**
   * The nearest entity carrying a [Townsfolk] component.
   *
   * A linear scan, which is fine for a GM command and would not be for anything else - the real handler
   * is handed the entity the player clicked.
   */
  private fun nearestTownsperson(x: Long, y: Long): EntityId? {
    return world.read {
      var best: EntityId? = null
      var bestDistance = Long.MAX_VALUE

      query(Townsfolk::class, Position::class).each { id ->
        val position = get<Position>().toVec3L()
        val dx = position.x - x
        val dy = position.y - y
        val distance = dx * dx + dy * dy

        if (distance < bestDistance && distance <= RANGE * RANGE) {
          bestDistance = distance
          best = id
        }
      }

      best
    }
  }

  private fun describe(speaker: Speaker, node: ConversationNode): List<String> {
    val lines = mutableListOf("${speaker.name} the ${speaker.occupation.label}: ${render(node.speech)}")

    node.options.forEachIndexed { index, option ->
      lines.add("  ${index + 1}) [${option.kind}] ${render(option.line)}")
    }
    if (node.options.isEmpty()) {
      lines.add("  (nothing more to say)")
    }

    return lines
  }

  private fun render(line: Line): String {
    if (line.args.isEmpty()) {
      return line.key
    }

    val args = line.args.entries.joinToString(", ") { (name, arg) -> "$name=${value(arg)}" }

    return "${line.key} {$args}"
  }

  private fun value(arg: DialogArg): String {
    return when (arg) {
      is DialogArg.Text -> arg.value
      is DialogArg.Name -> arg.value
      is DialogArg.Token -> "<${arg.key}>"
      is DialogArg.Number -> arg.value.toString()
      is DialogArg.Entity -> "entity:${arg.entityId}"
      is DialogArg.Item -> "item:${arg.itemId}"
      is DialogArg.Skill -> "skill:${arg.skillId}"
    }
  }

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }

  private companion object {
    private val CMD_REGEX = Regex("""^/talk(?:\s+(\d+))?$""")

    /** Far enough to catch somebody across a street, near enough not to pick the wrong villager. */
    private const val RANGE = 12L
  }
}
