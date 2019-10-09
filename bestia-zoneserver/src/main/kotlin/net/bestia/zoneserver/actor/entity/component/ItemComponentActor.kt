package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.ItemComponent
import net.bestia.zoneserver.entity.component.TagComponent

@ActorComponent(ItemComponent::class)
class ItemComponentActor(
    tagComponent: TagComponent
) : ComponentActor<TagComponent>(tagComponent)