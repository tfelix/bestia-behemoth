package net.bestia.entity.component.interceptor;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.EntityStatusUpdateMessage;
import net.bestia.messages.internal.entity.EntityComponentStateMessage;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.StatusComponent;

/**
 * The {@link StatusComponentInterceptor} creates updating entity actor which
 * will call tick jobs for regeneration and it will also perform updates to the
 * client if a value of a status has changed.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class StatusComponentInterceptor extends BaseComponentInterceptor<StatusComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(StatusComponentInterceptor.class);

	private final MessageApi msgApi;

	@Autowired
	public StatusComponentInterceptor(MessageApi actorApi) {
		super(StatusComponent.class);

		this.msgApi = Objects.requireNonNull(actorApi);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, StatusComponent comp) {

		LOG.debug("Component {} is updated.", comp);

		// Check if its a player and needs updates of the entity status.
		final Optional<PlayerComponent> playerComp = entityService.getComponent(entity, PlayerComponent.class);

		if (!playerComp.isPresent()) {
			return;
		}

		final long accId = playerComp.get().getOwnerAccountId();

		final EntityStatusUpdateMessage msg = new EntityStatusUpdateMessage(
				accId,
				entity.getId(),
				comp.getStatusPoints(),
				comp.getOriginalStatusPoints(),
				comp.getConditionValues(),
				comp.getStatusBasedValues());
		msgApi.sendToClient(msg);

	}

	/**
	 * We need to notify the entity actor that a new status component was
	 * attached and so needs to start the regeneration ticks for this component.
	 */
	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, StatusComponent comp) {

		LOG.debug("Component {} is created.", comp);

		final EntityComponentStateMessage msg = EntityComponentStateMessage.install(entity.getId(), comp.getId());
		msgApi.sendToEntity(msg);

	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, StatusComponent comp) {
		// Stop the actor timing the entity component.
		LOG.debug("Component {} is deleted.", comp);

		final EntityComponentStateMessage msg = EntityComponentStateMessage.remove(comp.getEntityId(), comp.getId());
		msgApi.sendToEntity(msg);
	}

}
