package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.component.PlayerComponentMessage;
import net.bestia.messages.entity.EntityComponentDeleteMessage;
import net.bestia.messages.entity.EntityComponentEnvelope;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;

/**
 * After a {@link PlayerComponent} is added to an entity the referenced player
 * bestia needs to be found and updated with the player bestia id so existing
 * entities can later be re-identified if the user re-connects to the system.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class PlayerComponentInterceptor extends BaseComponentInterceptor<PlayerComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerComponentInterceptor.class);

	private final PlayerBestiaDAO playerBestiaDao;
	private final MessageApi msgApi;

	@Autowired
	public PlayerComponentInterceptor(PlayerBestiaDAO playerBestiaDao, MessageApi msgApi) {
		super(PlayerComponent.class);

		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.msgApi = Objects.requireNonNull(msgApi);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, PlayerComponent comp) {
		// no op.
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, PlayerComponent comp) {

		LOG.debug("intercept onCreate: PlayerComponent.");

		final long pbid = comp.getPlayerBestiaId();
		final PlayerBestia playerBestia = playerBestiaDao.findOne(pbid);

		if (playerBestia == null) {
			LOG.warn("Could not find player bestia with id: {}.", pbid);
			return;
		}

		playerBestia.setEntityId(entity.getId());
		playerBestiaDao.save(playerBestia);

		// Create a update message now for this component.
		final PlayerComponentMessage msg = new PlayerComponentMessage(pbid,
				playerBestia.getOrigin().getDatabaseName(),
				playerBestia.getName());
		final EntityComponentEnvelope ece = EntityComponentEnvelope.forPayload(comp.getOwnerAccountId(), entity.getId(),
				EntityComponentEnvelope.componentName(PlayerComponent.class), msg);
		msgApi.sendToClient(ece);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, PlayerComponent comp) {

		LOG.debug("intercept onCreate: PlayerComponent.");

		final long pbid = comp.getPlayerBestiaId();
		final PlayerBestia playerBestia = playerBestiaDao.findOne(pbid);

		if (playerBestia == null) {
			LOG.warn("Could not find player bestia with id {}.", pbid);
			return;
		}

		playerBestia.setEntityId(0L);
		playerBestiaDao.save(playerBestia);

		// Send the delete envelope message to remove this component again.
		EntityComponentDeleteMessage ecdm = new EntityComponentDeleteMessage(comp.getOwnerAccountId(), 
				entity.getId(),
				comp.getId());
		msgApi.sendToClient(ecdm);
	}

}
