package net.bestia.zoneserver.entity

import net.bestia.messages.entity.Interaction
import net.bestia.zoneserver.entity.component.TagComponent
import org.springframework.stereotype.Service

import java.util.*

/**
 * Service to control the interaction between entities. Usually in this
 * interaction some sort of scripting is involved to determine the art of
 * interactions which are possible.
 *
 * @author Thomas Felix
 */
@Service
class InteractionService {

  /**
   * Asks the entity for all types of interactions which are possible with it
   * by the certain entity. It might be possible that the interaction
   * possibilities are dependent upon the invoker. Usually the questioning
   * part is also an InteractionComponent unit.
   *
   * The interaction is determined mostly by the tags of the entity on the
   * first place. In case of living and thinking entities (like NPCs) the NPC
   * itself is questioned via a script routine to refine the interaction
   * possibilities on a per player basis.
   *
   * But also items are able to control via a script if the given player
   * should be able to interact with them. To determine this a script call is
   * made. See the InteractionScriptEnv for further information.
   *
   * @return A set of possible interactions.
   */
  fun getPossibleInteractions(source: Entity, target: Entity): Set<Interaction> {
    // We cant interact with untagged entity.
    val tagComp = target.tryGetComponent(TagComponent::class.java) ?: return emptySet()

    val interactTypes = HashSet<Interaction>()

    // Check if the target is an item.
    if (tagComp.has(TagComponent.ITEM)) {
      interactTypes.add(Interaction.PICKUP)
      interactTypes.add(Interaction.ATTACK)
    }

    if (tagComp.has(TagComponent.MOB)) {
      interactTypes.add(Interaction.ATTACK)
    }

    if (tagComp.has(TagComponent.NPC)) {
      interactTypes.add(Interaction.INTERACT)
    }

    // FIXME The distance between the interaction requester and the entity should also be considered. Via script?
    // NPC und NPC
    // PC und NPC
    // TODO Call the script calculating the correct interactions.

    return interactTypes
  }
}
