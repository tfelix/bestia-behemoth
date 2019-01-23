package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.TagComponent

@ActorComponent(TagComponent::class)
class TagComponentActor(
    tagComponent: TagComponent
) : ComponentActor<TagComponent>(tagComponent)