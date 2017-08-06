package net.bestia.zoneserver.actor.ui;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.ui.ClientVarMessage;
import net.bestia.messages.ui.ClientVarRequestMessage;
import net.bestia.model.domain.ClientVar;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.service.ClientVarService;

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * as well as sending requested shortcuts back to the client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClientVarActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "clientvar";

	private final ClientVarService cvarService;

	@Autowired
	public ClientVarActor(ClientVarService cvarService) {

		this.cvarService = Objects.requireNonNull(cvarService);
	}
	
	@Override
	public void preStart() throws Exception {
		//B
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ClientVarRequestMessage.class, this::handleCvarReqest)
				.build();
	}

	/**
	 * Gets all current shortcuts and sends them back to the client.
	 * 
	 * @param msg
	 *            The request.
	 */
	private void handleCvarReqest(ClientVarRequestMessage msg) {

		switch (msg.getMode()) {
		case DEL:
			handleCvarDelete(msg);
			break;
		case SET:
			handleCvarSet(msg);
			break;
		case REQ:
			handleCvarReq(msg);
			break;
		default:
			LOG.warning("Unknown mode in cvar msg: {}.", msg.getMode().toString());
			return;
		}

	}

	/**
	 * Handles the setting of the cvar.
	 * 
	 * @param msg
	 */
	private void handleCvarSet(ClientVarRequestMessage msg) {
		cvarService.set(msg.getAccountId(), msg.getKey(), msg.getData());
	}

	/**
	 * Deletes the given key.
	 * 
	 * @param msg
	 *            The message.
	 */
	private void handleCvarDelete(ClientVarRequestMessage msg) {
		if (cvarService.isOwnerOfVar(msg.getAccountId(), msg.getKey())) {
			cvarService.delete(msg.getAccountId(), msg.getKey());
		}
	}

	/**
	 * Retrieves the requested key from the server.
	 * 
	 * @param msg
	 *            The messge.
	 */
	private void handleCvarReq(ClientVarRequestMessage msg) {
		final long accId = msg.getAccountId();
		final String key = msg.getKey();

		if (!cvarService.isOwnerOfVar(accId, key)) {
			return;
		}

		final ClientVar cvar = cvarService.find(accId, key);
		final ClientVarMessage cvarMsg = new ClientVarMessage(accId, msg.getUuid(), cvar.getData());
		AkkaSender.sendClient(getContext(), cvarMsg);
	}

}
