package net.bestia.zoneserver.actor.guild

import net.bestia.messages.guild.GuildMessage
import net.bestia.messages.guild.GuildRequestMessage
import net.bestia.model.dao.GuildDAO
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.guild.GuildService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * The actor will reply to guild requests messages which will provide details to
 * the user about the requested guild.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class GuildRequestActor(
        private val guildService: GuildService,
        private val guildDao: GuildDAO
) : BaseClientMessageRouteActor() {
  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(GuildRequestMessage::class.java, this::onRequest)
  }

  private fun onRequest(msg: GuildRequestMessage) {
    val guildId = msg.getRequestedGuildId()

    val isRequesterInGuild = guildService.isInGuild(msg.accountId, guildId)
    if (!isRequesterInGuild) {
      return
    }

    guildDao.findOne(guildId).ifPresent { guild ->
      val gmsg = GuildMessage(msg.accountId, guild)
      sendClient.tell(gmsg, self)
    }
  }

  companion object {
    const val NAME = "requestGuild"
  }
}