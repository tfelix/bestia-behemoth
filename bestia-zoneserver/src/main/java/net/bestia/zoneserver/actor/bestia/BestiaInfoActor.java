package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.RequestBestiaInfoMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.AccountZoneService;

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaInfoActor extends BestiaRoutingActor {

	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(RequestBestiaInfoMessage.class)));
	
	private final AccountZoneService accountService;

	@Autowired
	public BestiaInfoActor(AccountZoneService accService) {
		
		this.accountService = Objects.requireNonNull(accService);
	}
	
	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Sending bestia information.");
		
		final RequestBestiaInfoMessage rbimsg = (RequestBestiaInfoMessage) msg;
		final Set<PlayerBestia> bestias = accountService.getAllBestias(rbimsg.getAccountId());
		
		// We must send for each bestia a single message.
		final BestiaInfoMessage bimsg = new BestiaInfoMessage(rbimsg);
		
	}
}
