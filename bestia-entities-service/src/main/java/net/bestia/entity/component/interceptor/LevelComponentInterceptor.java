package net.bestia.entity.component.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.component.PlayerComponentMessage;
import net.bestia.messages.entity.EntityComponentEnvelope;
import net.bestia.model.dao.PlayerBestiaDAO;

/**
 * Intercepts the level component and will send a special level message
 * depending if the component is attached and send to the owner or to all
 * others.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class LevelComponentInterceptor extends BaseComponentInterceptor<LevelComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerComponentInterceptor.class);

	private final PlayerBestiaDAO playerBestiaDao;
	private final MessageApi msgApi;

	LevelComponentInterceptor(PlayerBestiaDAO playerBestiaDao, MessageApi msgApi) {
		super(LevelComponent.class);

		this.msgApi = msgApi;
		this.playerBestiaDao = playerBestiaDao;
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, LevelComponent comp) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, LevelComponent comp) {
		// Create a update message now for this component.
		final PlayerComponentMessage msg = new PlayerComponentMessage(pbid,
				playerBestia.getOrigin().getDatabaseName(),
				playerBestia.getName());
		final EntityComponentEnvelope ece = EntityComponentEnvelope.forPayload(comp.getOwnerAccountId(), entity.getId(),
				EntityComponentEnvelope.componentName(PlayerComponent.class), msg);
		msgApi.sendToClient(ece);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, LevelComponent comp) {
		// TODO Auto-generated method stub

		// Create a update message now for this component.
		final PlayerComponentMessage msg = new PlayerComponentMessage(pbid,
				playerBestia.getOrigin().getDatabaseName(),
				playerBestia.getName());
		final EntityComponentEnvelope ece = EntityComponentEnvelope.forPayload(comp.getOwnerAccountId(), entity.getId(),
				EntityComponentEnvelope.componentName(PlayerComponent.class), msg);
		msgApi.sendToClient(ece);
	}

}
