package net.bestia.zoneserver.actor.chat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.IdEntity;
import net.bestia.zoneserver.service.AccountZoneService;
import net.bestia.zoneserver.service.EntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the apropriate receivers.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ChatActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "chat";

	private final AccountZoneService accService;
	private final PlayerEntityService playerEntityService;
	private final EntityService entityService;
	
	private final ActorRef chatCommandActor;

	@Autowired
	public ChatActor(AccountZoneService accService, PlayerEntityService playerEntityService,
			EntityService entityService) {
		super(Arrays.asList(ChatMessage.class));
		
		this.accService = Objects.requireNonNull(accService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.entityService = Objects.requireNonNull(entityService);
		
		this.chatCommandActor = createActor(ChatCommandActor.class);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ChatMessage chatMsg = (ChatMessage) msg;
		
		if(chatMsg.getText().startsWith(ChatCommandActor.CMD_PREFIX)) {
			// This is a chat command.
			chatCommandActor.tell(chatMsg, getSelf());
		}

		switch (chatMsg.getChatMode()) {
		case PUBLIC:
			handlePublic(chatMsg);
			break;
		case WHISPER:
			handleWhisper(chatMsg);
			break;

		default:
			LOG.warning("Message type not yet supported.");
			break;
		}
	}

	/**
	 * Sends a public message to all clients in sight.
	 */
	private void handlePublic(ChatMessage chatMsg) {
		final long accId = chatMsg.getAccountId();
		final PlayerBestiaEntity pbe = playerEntityService.getActivePlayerEntity(accId);

		if (pbe == null) {
			return;
		}

		final Collection<IdEntity> entities = entityService.getEntitiesInSight(pbe);
		
		final List<PlayerBestiaEntity> sightPbe = entities.stream()
				.filter(x -> (x instanceof PlayerBestiaEntity))
				.map(x -> (PlayerBestiaEntity) x)
				.collect(Collectors.toList());

		sightPbe.parallelStream()
				.map(x -> x.getAccountId())
				.map(receiverAccId -> ChatMessage.getEchoMessage(receiverAccId,
						chatMsg))
				.forEach(msg -> sendClient(msg));
	}

	/**
	 * Handles an incoming whisper message.
	 */
	private void handleWhisper(ChatMessage chatMsg) {
		// Cant handle with no receiver name.
		if (chatMsg.getReceiverNickname() == null) {
			return;
		}

		final Account acc = accService.getOnlineAccountByName(chatMsg.getReceiverNickname());

		if (acc == null) {
			return;
		}

		final ChatMessage reply = ChatMessage.getEchoMessage(acc.getId(), chatMsg);
		sendClient(reply);
	}

}
