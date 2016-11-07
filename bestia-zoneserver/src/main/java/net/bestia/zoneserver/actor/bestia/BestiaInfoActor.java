package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.RequestBestiaInfoMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.EntityService;

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class BestiaInfoActor extends BestiaRoutingActor {

	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final EntityService entityService;

	@Autowired
	public BestiaInfoActor(EntityService entityService) {
		super(Arrays.asList(RequestBestiaInfoMessage.class));
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));
		
		final RequestBestiaInfoMessage rbimsg = (RequestBestiaInfoMessage) msg;
		final Set<PlayerBestiaEntity> bestias = entityService.getPlayerBestiaEntities(rbimsg.getAccountId());
		
		for(PlayerBestiaEntity bestia : bestias) {
			// We must send for each bestia a single message.
			final BestiaInfoMessage bimsg = new BestiaInfoMessage(rbimsg, 
					bestia.getPlayerBestia(), 
					bestia.getStatusPoints());
			sendClient(bimsg);
		}		
	}
}
