package net.bestia.zoneserver.actor.guild;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.bestia.messages.guild.GuildMessage;
import net.bestia.messages.guild.GuildRequestMessage;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.model.dao.GuildDAO;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.guild.GuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * The actor will reply to guild requests messages which will provide details to
 * the user about the requested guild.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class GuildRequestActor extends AbstractActor {

	public static final String NAME = "requestGuild";

	private final GuildService guildService;
	private final GuildDAO guildDao;
	private final ActorRef sendClient;

	@Autowired
	public GuildRequestActor(GuildService guildService, 
			GuildDAO guildDao) {

		this.guildService = Objects.requireNonNull(guildService);
		this.guildDao = Objects.requireNonNull(guildDao);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(GuildRequestMessage.class, this::onRequest)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(InventoryListRequestMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void onRequest(GuildRequestMessage msg) {
		final int guildId = msg.getRequestedGuildId();
		
		// Check if the member is inside the guild.
		if(!guildService.isInGuild(msg.getAccountId(), guildId)) {
			return;
		}
		
		guildDao.findOne(guildId).ifPresent(guild -> {
			final GuildMessage gmsg = new GuildMessage(msg.getAccountId(), guild);
			sendClient.tell(gmsg, getSelf());
		});
	}
}
