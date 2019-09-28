package net.bestia.zoneserver.actor.guild

import net.bestia.messages.guild.GuildRequestMessage
import net.bestia.messages.guild.GuildResponseMessage
import net.bestia.model.findOneOrThrow
import net.bestia.model.guild.GuildRepository
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.guild.GuildService

/**
 * The actor will reply to guild requests messages which will provide details to
 * the user about the requested guild.
 *
 * @author Thomas Felix
 */
@Actor
class GuildRequestActor(
    private val guildService: GuildService,
    private val guildDao: GuildRepository
) : DynamicMessageRoutingActor() {
  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(GuildRequestMessage::class.java, this::onRequest)
  }

  private fun onRequest(msg: GuildRequestMessage) {
    val guildId = msg.requestedGuildId

    val isRequesterInGuild = guildService.isInGuild(msg.accountId, guildId)
    if (!isRequesterInGuild) {
      return
    }

    val guild = guildDao.findOneOrThrow(guildId)
    val gmsg = GuildResponseMessage(msg.accountId, guild)
    sendClient.tell(gmsg, self)
  }

  companion object {
    const val NAME = "requestGuild"
  }
}
