package net.bestia.zone.chat

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.Speech
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A creature's line in public chat, signed with its species so the chat log says who spoke. The server knows a
 * species only by identifier - its display name lives in the client's translation file - so it is spelled out
 * from that.
 */
@Component
class CreatureSpeech(
  private val outMessageProcessor: OutMessageProcessor,
  private val bestiaCatalogue: BestiaCatalogue,
) : Speech {

  override fun say(world: World, speakerId: EntityId, text: String) {
    val line = ChatSMSG(
      type = ChatType.PUBLIC,
      text = text,
      senderUsername = speakerName(world, speakerId),
      senderEntityId = speakerId
    )

    outMessageProcessor.sendToObserversOf(world, speakerId, line)
  }

  private fun speakerName(world: World, speakerId: EntityId): String {
    val visual = world.get(speakerId, EntityVisual::class)?.takeIf { it.kind == VisualKind.BESTIA }

    val identifier = visual?.let { bestiaCatalogue.find(it.id)?.identifier } ?: return UNKNOWN_SPEAKER

    return identifier.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
  }

  private companion object {
    const val UNKNOWN_SPEAKER = "???"
  }
}
