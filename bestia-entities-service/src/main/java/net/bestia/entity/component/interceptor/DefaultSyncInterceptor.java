package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSync;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.SyncType;
import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.EntityComponentDeleteMessage;
import net.bestia.messages.entity.EntityComponentMessage;

/**
 * This component interceptor will test all components if a change will lead to
 * client notification or actor notification. How the change of the component is
 * determined is given by its annotations.
 * 
 * @author Thomas Felix
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

		// TODO Hier fehlt noch der check.
		
		final EntityComponentDeleteMessage ecdMsg = new EntityComponentDeleteMessage(0, entity.getId(), comp.getId());
		msgApi.sendToActiveClientsInRange(ecdMsg);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, Component comp) {
		LOG.debug("Component {} is updated.", comp);
		performComponentSync(entityService, entity, comp);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, Component comp) {
		LOG.debug("Component {} is created.", comp);
		performComponentSync(entityService, entity, comp);
	}

	/**
	 * Checks if the given {@link Component} should be synced towards the
	 * clients.
	 * 
	 * @param comp
	 *            The component to possibly sync.
	 */
	private void performComponentSync(EntityService entityService, Entity entity, Component comp) {

		// Don't sync if not annotated.
		if (!comp.getClass().isAnnotationPresent(ComponentSync.class)) {
			return;
		}

		final EntityComponentMessage ecm = new EntityComponentMessage(0, comp);
		ComponentSync syncAnno = comp.getClass().getAnnotation(ComponentSync.class);

		if (syncAnno.value() == SyncType.ALL) {
			msgApi.sendToActiveClientsInRange(ecm);
		} else {
			// Message should be send to a single clients. Thus we need to get the receiving account id.
			entityService.getComponent(entity, PlayerComponent.class).ifPresent(pc -> {
				final long receivingAccId = pc.getOwnerAccountId();
				msgApi.sendToClient(ecm.createNewInstance(receivingAccId));
			});
			
		}
	}
}
