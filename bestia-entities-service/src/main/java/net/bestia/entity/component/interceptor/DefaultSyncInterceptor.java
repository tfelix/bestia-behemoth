package net.bestia.entity.component.interceptor;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.EntityComponentDeleteMessage;
import net.bestia.messages.entity.EntityComponentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * This component interceptor will test all components if a change will lead to
 * client notification or actor notification. How the change of the component is
 * determined is given by its annotations.
 */
public class DefaultSyncInterceptor extends BaseComponentInterceptor<Component> {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultSyncInterceptor.class);
	private final MessageApi msgApi;

	@Autowired
	public DefaultSyncInterceptor(MessageApi msgApi) {
		super(Component.class);

		this.msgApi = Objects.requireNonNull(msgApi);
	}

	@Override
	public void triggerCreateAction(EntityService entityService, Entity entity, Component comp) {
		onCreateAction(entityService, entity, comp);
	}

	@Override
	public void triggerDeleteAction(EntityService entityService, Entity entity, Component comp) {
		onDeleteAction(entityService, entity, comp);
	}

	@Override
	public void triggerUpdateAction(EntityService entityService, Entity entity, Component comp) {
		onUpdateAction(entityService, entity, comp);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, Component comp) {
		LOG.debug("Component {} is deleted.", comp);

		// TODO This components must be filtered better.
		if (!(comp instanceof PositionComponent)) {
			return;
		}

		final EntityComponentDeleteMessage ecdMsg = new EntityComponentDeleteMessage(0, entity.getId(), comp.getId());
		msgApi.sendToActiveClientsInRange(ecdMsg);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, Component comp) {
		LOG.debug("Component {} is updated.", comp);

		// TODO This components must be filtered better.
		if (!(comp instanceof PositionComponent)) {
			return;
		}

		// Prepare to send this update to all connected players.
		sendComponentMessageToClients(entity, comp);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, Component comp) {
		LOG.debug("Component {} is created.", comp);

		// TODO This components must be filtered better.
		if (!(comp instanceof PositionComponent)) {
			return;
		}

		sendComponentMessageToClients(entity, comp);
	}

	private void sendComponentMessageToClients(Entity entity, Component comp) {
		// Prepare to send this update to all connected players.
		final EntityComponentMessage ecm = new EntityComponentMessage(0, entity.getId(), comp);
		msgApi.sendToActiveClientsInRange(ecm);
	}
}
