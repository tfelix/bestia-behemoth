package net.bestia.zoneserver.actor.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.guild.GuildService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * Handles guild chats. 
 * It sends the chat message to all online guild members. 
 * If the user is no member of a guild it does nothing.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class GuildChatActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "guild";
	
	private final GuildService guildService;
	private final net.bestia.zoneserver.service.PlayerEntityService playerEntityService;
	private final ActorRef sendActor;

	@Autowired
	public GuildChatActor(GuildService guildService,
			PlayerEntityService playerEntityService) {

		this.guildService = Objects.requireNonNull(guildService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		sendActor = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ChatMessage.class, this::handleGuild)
				.build();
	}

	/**
	 * Sends a public message to all clients in sight.
	 */
	private void handleGuild(ChatMessage chatMsg) {
		if(chatMsg.getChatMode() != ChatMessage.Mode.GUILD) {
			LOG.warning("Message {} is no guild message.", chatMsg);
			return;
		}
	
		final long playerBestiaId = playerEntityService.getActivePlayerBestiaId(chatMsg.getAccountId());
		
		if(playerBestiaId == 0) {
			return;
		}

		guildService.getGuildMembersFromPlayer(playerBestiaId).stream()
		.filter(member -> member.getEntityId() != 0)
		.map(PlayerBestia::getEntityId)
		.map(chatMsg::createNewInstance)
		.forEach(msg -> {
			sendActor.tell(msg, getSelf());
		});
	}

}
