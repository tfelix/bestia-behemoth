package net.bestia.zoneserver.actor.ui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.ui.ShortcutsMessage;
import net.bestia.messages.ui.ShortcutsRequestMessage;
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
public class ShortcutActor extends BestiaRoutingActor {
	
	public static final String NAME = "shortcuts";

	@Autowired
	public ShortcutActor() {

	}

	@Override
	protected void handleMessage(Object msg) {
		if (msg instanceof ShortcutsRequestMessage) {
			handleShortcutReqest((ShortcutsRequestMessage) msg);
		} else if (msg instanceof ShortcutsMessage) {
			handleShortcutSave((ShortcutsMessage) msg);
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
	private void handleShortcutReqest(ShortcutsRequestMessage msg) {

	}

	private void handleShortcutSave(ShortcutsMessage msg) {

	}

}
