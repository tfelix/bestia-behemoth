package net.bestia.zoneserver.actor.ui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.ui.ClientVarMessage;
import net.bestia.messages.ui.ClientVarRequestMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * aswell as sending reuqested shortcuts back to the client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClientVarActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "clientvar";
	
	

	@Autowired
	public ClientVarActor() {

	}

	@Override
	protected void handleMessage(Object msg) {
		if (msg instanceof ClientVarRequestMessage) {
			handleCvarReqest((ClientVarRequestMessage) msg);
		} else if (msg instanceof ClientVarMessage) {
			handleShortcutSave((ClientVarMessage) msg);
		} else {
			unhandled(msg);
		}
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

			break;

		case REQ:

			break;
		}

	}

	/**
	 * Deletes the given key.
	 * 
	 * @param msg
	 */
	private void handleCvarDelete(ClientVarRequestMessage msg) {
		
	}

	private void handleCvarReqest(ClientVarMessage msg) {

	}

}
