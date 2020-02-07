package net.bestia.zoneserver.actor.guild

import akka.actor.ActorRef
import net.bestia.messages.guild.GuildRequest
import net.bestia.messages.guild.GuildResponse
import net.bestia.model.findOneOrThrow
import net.bestia.model.guild.GuildRepository
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.guild.GuildService
import org.springframework.beans.factory.annotation.Qualifier

/**
 * The actor will reply to guild requests messages which will provide details to
 * the user about the requested guild.
 *
 * @author Thomas Felix
 */
@Actor
class GuildRequestActor(
    private val guildService: GuildService,
    private val guildDao: GuildRepository,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(GuildRequest::class.java, this::onRequest)
  }

  private fun onRequest(msg: GuildRequest) {
    val guildId = msg.requestedGuildId

    val isRequesterInGuild = guildService.isInGuild(msg.accountId, guildId)
    if (!isRequesterInGuild) {
      return
    }

    val guild = guildDao.findOneOrThrow(guildId)
    val gmsg = GuildResponse(msg.accountId)
    sendClientActor.tell(gmsg, self)
  }

  companion object {
    const val NAME = "requestGuild"
  }
}
