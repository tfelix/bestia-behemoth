package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.OriginalStatusComponent

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@ActorComponent(OriginalStatusComponent::class)
class OriginalStatusComponentActor(
    originalStatusComponent: OriginalStatusComponent
) : ComponentActor<OriginalStatusComponent>(originalStatusComponent)

