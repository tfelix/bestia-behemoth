package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.GuildComponent

@ActorComponent(GuildComponent::class)
class GuildComponentActor(
    guildComponent: GuildComponent
) : ComponentActor<GuildComponent>(guildComponent)