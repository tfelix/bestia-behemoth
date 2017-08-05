package net.bestia.zoneserver.actor.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.PartyDAO;
import net.bestia.model.domain.Party;
import net.bestia.zoneserver.AkkaSender;

/**
 * Handles party chat messages.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PartyChatActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "party";

	private final PartyDAO partyDao;

	@Autowired
	public PartyChatActor(PartyDAO partyDao) {

		this.partyDao = Objects.requireNonNull(partyDao);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ChatMessage.class, this::handleParty)
				.build();
	}

	/**
	 * Handles the party message. Finds all member in this party and then send
	 * the message to them.
	 */
	private void handleParty(ChatMessage chatMsg) {
		// Sanity check.
		if (chatMsg.getChatMode() != ChatMessage.Mode.PARTY) {
			LOG.warning("Can not handle non party chat messages: {}.", chatMsg);
			unhandled(chatMsg);
			return;
		}

		final Party party = partyDao.findPartyByMembership(chatMsg.getAccountId());
		if (party == null) {
			// not a member of a party.
			LOG.debug("Account {} is no member of any party.", chatMsg.getAccountId());
			final ChatMessage replyMsg = ChatMessage.getSystemMessage(chatMsg.getAccountId(),
					"Not a member of a party.");
			AkkaSender.sendClient(getContext(), replyMsg);
			return;
		}

		party.getMembers().forEach(member -> {
			final ChatMessage reply = chatMsg.createNewInstance(member.getId());
			AkkaSender.sendClient(getContext(), reply);
		});
	}

}
