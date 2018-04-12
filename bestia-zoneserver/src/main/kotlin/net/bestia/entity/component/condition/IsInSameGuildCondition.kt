package net.bestia.entity.component.condition

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import net.bestia.entity.component.GuildComponent
import net.bestia.getOrNull

/**
 * Only returns true if the sender is in the same party as the receiver.
 */
class IsInSameGuildCondition : SyncCondition {
  override fun doSync(receiver: Entity, entity: Entity, component: Component, entityService: EntityService): Boolean {

    val receiverGuild = entityService.getComponent(receiver, GuildComponent::class.java).getOrNull() ?: return false
    val senderGuild = entityService.getComponent(entity, GuildComponent::class.java).getOrNull() ?: return false

    return receiverGuild.guildId == senderGuild.guildId
  }
}