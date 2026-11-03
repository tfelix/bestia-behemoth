package net.bestia.zone.townsfolk.conversation

import net.bestia.zone.ecs.core.World
import net.bestia.zone.townsfolk.TownsfolkRegard
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** How a townsperson receives a Master, by what they and their town think of them. */
@Component
class HonorReception(
  private val regard: TownsfolkRegard,
  private val lines: ConversationLineCatalogue,
) {

  /** A curt line and nothing to ask, or null when [speaker] will talk to [actor] as to anybody. */
  fun coldShoulder(world: World, actor: EntityId, speaker: Speaker): ConversationNode? {
    if (!regard.isUnfriendly(world, actor, speaker.settlement, speaker.identity)) {
      return null
    }

    return ConversationNode(lines.lineFor(speaker, ConversationKeys.COLD_SHOULDER), emptyList())
  }
}
