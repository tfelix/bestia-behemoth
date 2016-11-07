package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.AccountZoneService;
import net.bestia.zoneserver.service.EntityService;

/**
 * This is a direct child of the login actor. He is responsible for spawning the
 * player bestias in case a login was successful.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class SpawnPlayerEntityActor extends BestiaRoutingActor {
	
	public static String NAME = "spawnPlayerEntities";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final AccountZoneService accService;
	private final EntityService entityService;
	
	@Autowired
	public SpawnPlayerEntityActor(AccountZoneService accService, EntityService entityService) {
		super(Arrays.asList(ClientConnectionStatusMessage.class));
		this.accService = Objects.requireNonNull(accService);
		this.entityService = Objects.requireNonNull(entityService);
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
			entityService.putPlayerBestias(bestias);
			
			// TODO Test
			Set<PlayerBestiaEntity> tests = entityService.getPlayerBestiaEntities(ccsm.getAccountId());
			LOG.debug(tests.toString());
		} else {
			// Remove all bestias entities for this account.
			LOG.debug(String.format("DeSpawning bestias for acc id: %d", ccsm.getAccountId()));
			entityService.removePlayerBestias(ccsm.getAccountId());
			Set<PlayerBestiaEntity> tests = entityService.getPlayerBestiaEntities(ccsm.getAccountId());
			LOG.debug(tests.toString());
		}
	}
}
