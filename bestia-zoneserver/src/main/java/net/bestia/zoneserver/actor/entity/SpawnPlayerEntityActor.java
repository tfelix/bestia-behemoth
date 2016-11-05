package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.EntityService;

/**
 * This is a direct child of the login actor. He is responsible for spawning the
 * player bestias in case a login was successful.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class SpawnPlayerEntityActor extends BestiaRoutingActor {
	
	public static String NAME = "spawnPlayerBestias";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(ClientConnectionStatusMessage.class)));
	
	private final AccountService accService;
	private final EntityService entityService;
	
	@Autowired
	public SpawnPlayerEntityActor(AccountService accService, EntityService entityService) {
		
		this.accService = Objects.requireNonNull(accService);
		this.entityService = Objects.requireNonNull(entityService);
	}
	
	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));
		final ClientConnectionStatusMessage ccsm = (ClientConnectionStatusMessage) msg;
		
		if(ccsm.getState() == ConnectionState.CONNECTED) {
			// Spawn all bestia entities for this account into the world.
			final Set<PlayerBestiaEntity> bestias = accService
					.getAllBestias(ccsm.getAccountId())
					.parallelStream()
					.map(x -> new PlayerBestiaEntity(x))
					.collect(Collectors.toSet());
			LOG.debug(String.format("Spawning %d player bestias for acc id: %d", bestias.size(), ccsm.getAccountId()));
			bestias.forEach(pbe -> entityService.putPlayerBestias(pbe));
		} else {
			// Remove all bestias entities for this account.
			LOG.debug(String.format("DeSpawning bestias for acc id: %d", ccsm.getAccountId()));
			entityService.removePlayerBestias(ccsm.getAccountId());
		}
	}
}
