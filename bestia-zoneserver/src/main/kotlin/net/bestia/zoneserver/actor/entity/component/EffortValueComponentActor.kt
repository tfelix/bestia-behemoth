package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.EffortValueComponent
import net.bestia.zoneserver.entity.component.TagComponent

@ActorComponent(EffortValueComponent::class)
class EffortValueComponentActor(
    tagComponent: TagComponent
) : ComponentActor<TagComponent>(tagComponent)