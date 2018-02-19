package bestia.zoneserver.actor.ui;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.messages.ui.ClientVarMessage;
import bestia.messages.ui.ClientVarRequestMessage;
import bestia.model.domain.ClientVar;
import bestia.zoneserver.actor.SpringExtension;
import bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import bestia.zoneserver.actor.zone.SendClientActor;
import bestia.zoneserver.service.ClientVarService;

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * as well as sending requested shortcuts back to the client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClientVarActor extends ClientMessageDigestActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "clientvar";

	private final ClientVarService cvarService;
	private final ActorRef sendClient;

	@Autowired
	public ClientVarActor(ClientVarService cvarService, 
			ActorRef msgHub) {

		this.cvarService = Objects.requireNonNull(cvarService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
		
		this.redirectConfig.match(ClientVarRequestMessage.class, this::handleCvarReqest);
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
	 *            The message.
	 */
	private void handleCvarReq(ClientVarRequestMessage msg) {
		final long accId = msg.getAccountId();
		final String key = msg.getKey();

		if (!cvarService.isOwnerOfVar(accId, key)) {
			return;
		}

		final ClientVar cvar = cvarService.find(accId, key);
		final ClientVarMessage cvarMsg = new ClientVarMessage(accId, msg.getUuid(), cvar.getData());
		sendClient.tell(cvarMsg, getSelf());
	}

}
