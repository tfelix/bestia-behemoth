package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.MetadataComponent

@ActorComponent(MetadataComponent::class)
class MetadataComponentActor(
    component: MetadataComponent
) : ComponentActor<MetadataComponent>(component)