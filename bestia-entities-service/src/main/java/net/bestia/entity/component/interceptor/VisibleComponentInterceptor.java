package net.bestia.entity.component.interceptor;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityUpdateMessage;

/**
 * A visible component is sent to the client upon creation.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class VisibleComponentInterceptor extends BaseComponentInterceptor<VisibleComponent> {

	private final MessageApi msgApi;
	
	VisibleComponentInterceptor(MessageApi akkaApi) {
		super(VisibleComponent.class);

		this.msgApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, VisibleComponent comp) {
		final long eid = entity.getId();
		Optional<PositionComponent> posComp = entityService.getComponent(entity, PositionComponent.class);
		
		if(!posComp.isPresent()) {
			return;
		}
		
		final long x = posComp.get().getPosition().getX();
		final long y = posComp.get().getPosition().getY();
		
		final EntityUpdateMessage msg = new EntityUpdateMessage(0, eid, x, y, comp.getVisual(), EntityAction.UPDATE);
		msgApi.sendActiveInRangeClients(msg);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, VisibleComponent comp) {
		
		final long eid = entity.getId();
		Optional<PositionComponent> posComp = entityService.getComponent(entity, PositionComponent.class);
		
		if(!posComp.isPresent()) {
			return;
		}
		
		final long x = posComp.get().getPosition().getX();
		final long y = posComp.get().getPosition().getY();
		
		final EntityUpdateMessage msg = new EntityUpdateMessage(0, eid, x, y, comp.getVisual());
		msgApi.sendActiveInRangeClients(msg);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, VisibleComponent comp) {
		// no op.
	}

}
