package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.GuildComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(GuildComponent::class)
class GuildComponentActor(
    guildComponent: GuildComponent
) : ComponentActor<GuildComponent>(guildComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}