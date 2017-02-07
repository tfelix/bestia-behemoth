package net.bestia.zoneserver.actor.zone;

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
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * Manages the connection state of a client. This is needed to perform certain
 * lookups to retrieve the current actor path of the webserver a client is
 * connected to.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class LogoutActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "disconnectManager";

	private final AccountDAO accDao;
	private final PlayerBestiaService playerBestiaService;
	private final PlayerEntityService entityService;
	private final ConnectionService connectionService;

	@Autowired
	public LogoutActor(ConnectionService connectionService,
			PlayerEntityService entityService, PlayerBestiaService playerBestiaService, AccountDAO accDao) {
		super(Arrays.asList(ClientConnectionStatusMessage.class));

		this.connectionService = Objects.requireNonNull(connectionService);
		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.accDao = Objects.requireNonNull(accDao);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) msg;

		if (!(ccmsg.getState() == ConnectionState.CONNECTED)) {
			// Unregister.
			LOG.debug("Client disconnected: {}.", ccmsg);
			connectionService.removeClient(ccmsg.getAccountId());

			// Persist and remove all bestias entities for this account.
			LOG.debug(String.format("DeSpawning bestias for acc id: %d", ccmsg.getAccountId()));
			
			final Set<PlayerEntity> bestias = entityService.getPlayerEntities(ccmsg.getAccountId());
			entityService.removePlayerBestias(ccmsg.getAccountId());
			
			final Account acc = accDao.findOne(ccmsg.getAccountId());
			Set<PlayerBestia> pbs = bestias.stream().map(x -> x.restorePlayerBestia(acc)).collect(Collectors.toSet());
			playerBestiaService.saveBestias(pbs);
		}
	}
}
