package net.bestia.zoneserver.actor.entity;

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
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.PlayerEntityService;

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

	private final AccountDAO accountDao;
	private final PlayerEntityService entityService;

	@Autowired
	public BestiaInfoActor(PlayerEntityService entityService, AccountDAO accountDao) {
		super(Arrays.asList(RequestBestiaInfoMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.accountDao = Objects.requireNonNull(accountDao);

	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));

		final RequestBestiaInfoMessage rbimsg = (RequestBestiaInfoMessage) msg;

		final Set<PlayerBestiaEntity> bestias = entityService.getPlayerEntities(rbimsg.getAccountId());
		final Account owner = accountDao.findOne(rbimsg.getAccountId());

		for (PlayerBestiaEntity pbe : bestias) {			

			// Get the model updated with all the changed data.
			final PlayerBestia pb = pbe.restorePlayerBestia(owner);

			final BestiaInfoMessage bimsg = new BestiaInfoMessage(rbimsg.getAccountId(),
					pbe.getId(),
					pb,
					pbe.getStatusPoints());
			sendClient(bimsg);
		}
	}
}
