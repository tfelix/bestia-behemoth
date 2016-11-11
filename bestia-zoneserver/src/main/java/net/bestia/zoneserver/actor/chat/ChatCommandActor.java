package net.bestia.zoneserver.actor.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.BestiaActor;
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
public class ChatCommandActor extends BestiaActor {
	public static final String NAME = "chatCmd";
	public static final String CMD_PREFIX = "/";
	
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final AccountZoneService accService;

	@Autowired
	public ChatCommandActor(AccountZoneService accService, PlayerEntityService playerEntityService,
			EntityService entityService) {
		
		this.accService = Objects.requireNonNull(accService);
	}


	@Override
	public void onReceive(Object msg) throws Throwable {
		if(!(msg instanceof ChatMessage)) {
			unhandled(msg);
			return;
		}
		
		// TODO Chat Commands verarbeiten.
		
	}
}
