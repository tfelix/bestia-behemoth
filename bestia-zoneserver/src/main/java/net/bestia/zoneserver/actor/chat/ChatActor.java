package net.bestia.zoneserver.actor.chat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.domain.Account;
import net.bestia.model.map.Map;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Rect;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
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
	private final ActorRef responder;

	@Autowired
	public ChatActor(AccountZoneService accService, PlayerEntityService playerEntityService,
			EntityService entityService) {
		super(Arrays.asList(ChatMessage.class));
		this.accService = Objects.requireNonNull(accService);
		this.responder = createActor(SendClientActor.class);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ChatMessage chatMsg = (ChatMessage) msg;

		switch (chatMsg.getChatMode()) {
		case PUBLIC:
			handlePublic(chatMsg);
			break;
		case WHISPER:
			handleWhisper(chatMsg);
			break;

		case PARTY:
		case GUILD:
			// no op.
			break;
		default:
			LOG.warning("Message type not yet supported.");
			break;
		}
	}

	private void handlePublic(ChatMessage chatMsg) {
		final long accId = chatMsg.getAccountId();
		final PlayerBestiaEntity pbe = playerEntityService.getActivePlayerEntity(accId);

		if (pbe == null) {
			return;
		}

		final Point pos = pbe.getPosition();
		final Rect sightRect = new Rect(pos.getX() - Map.SIGHT_RANGE,
				pos.getY() - Map.SIGHT_RANGE,
				pos.getX() + Map.SIGHT_RANGE,
				pos.getY() + Map.SIGHT_RANGE);
		Collection<IdEntity> entities = entityService.getEntitiesInRange(sightRect);

		/*
		final List<PlayerBestiaEntity> sightPbe = entities
				.filter(x -> (x instanceof PlayerBestiaEntity))
				.map(x -> (PlayerBestiaEntity) x)
				.collect(Collectors.toList());

		sightPbe.parallelStream()
				.map(x -> x.getAccountId())
				.map(receiverAccId -> ChatMessage.getEchoMessage(receiverAccId, chatMsg))
				.forEach(msg -> sendClient(msg));*/
	}

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
		responder.tell(reply, getSelf());
	}

}
