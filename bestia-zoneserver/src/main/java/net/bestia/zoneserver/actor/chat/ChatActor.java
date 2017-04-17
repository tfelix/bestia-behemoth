package net.bestia.zoneserver.actor.chat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.PartyDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Party;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.chat.ChatCommandService;
import net.bestia.zoneserver.entity.ComponentService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.service.AccountZoneService;

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the appropriate receivers
 * which will then in turn work with the chat message or redirect it to the chat
 * command system.
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
	private final ChatCommandService chatCmdService;
	private final ComponentService compService;
	private final PartyDAO partyDao;

	@Autowired
	public ChatActor(AccountZoneService accService, PlayerEntityService playerEntityService,
			ChatCommandService chatCmdService, PartyDAO partyDao, ComponentService compService) {
		super(Arrays.asList(ChatMessage.class));

		this.accService = Objects.requireNonNull(accService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.chatCmdService = Objects.requireNonNull(chatCmdService);
		this.partyDao = Objects.requireNonNull(partyDao);
		this.compService = Objects.requireNonNull(compService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ChatMessage chatMsg = (ChatMessage) msg;

		if (chatCmdService.isChatCommand(chatMsg.getText())) {
			chatCmdService.executeChatCommand(chatMsg.getAccountId(), chatMsg.getText());
			return;
		}

		switch (chatMsg.getChatMode()) {
		case PUBLIC:
			handlePublic(chatMsg);
			break;
		case WHISPER:
			handleWhisper(chatMsg);
			break;
		case PARTY:
			handleParty(chatMsg);
			break;

		default:
			LOG.warning("Message type not yet supported.");
			break;
		}
	}

	/**
	 * Handles the party message. Finds all member in this party and then send
	 * the message to them.
	 */
	private void handleParty(ChatMessage chatMsg) {
		final Party party = partyDao.findPartyByMembership(chatMsg.getAccountId());
		if (party == null) {
			// not a member of a party.
			LOG.debug("Account {} is no member of any party.", chatMsg.getAccountId());
			sendClient(ChatMessage.getSystemMessage(chatMsg.getAccountId(), "Not a member of a party."));
			return;
		}

		party.getMembers().forEach(member -> {
			final ChatMessage reply = chatMsg.createNewInstance(member.getId());
			sendClient(reply);
		});
	}

	/**
	 * Sends a public message to all clients in sight.
	 */
	private void handlePublic(ChatMessage chatMsg) {
		final long accId = chatMsg.getAccountId();
		final Entity pbe = playerEntityService.getActivePlayerEntity(accId);

		if (pbe == null) {
			return;
		}

		// Add the current entity id to the message.
		final ChatMessage chatEntityMsg = new ChatMessage(accId, pbe.getId(), chatMsg);

		Optional<PositionComponent> pos = compService.getComponent(pbe, PositionComponent.class);

		if (!pos.isPresent()) {
			LOG.warning("Player bestia has no position component.");
			return;
		}

		// TODO Senden an alle Accounts in Reichweite in einen Service
		// auslagern.
		final List<Long> receiverAccIds = playerEntityService
				.getActiveAccountIdsInRange(Map.getViewRect(pos.get().getPosition()));

		receiverAccIds.stream().map(receiverAccId -> chatEntityMsg.createNewInstance(receiverAccId))
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
			LOG.debug("Whisper receiver {} not found.", chatMsg.getReceiverNickname());
			return;
		}

		final ChatMessage reply = chatMsg.createNewInstance(acc.getId());
		sendClient(reply);
	}

}
