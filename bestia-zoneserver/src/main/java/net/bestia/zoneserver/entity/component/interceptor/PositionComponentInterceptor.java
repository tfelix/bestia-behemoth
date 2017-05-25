package net.bestia.zoneserver.entity.component.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

@Component
public class PositionComponentInterceptor extends ComponentInterceptor<PositionComponent> {
	
	private static final Logger LOG = LoggerFactory.getLogger(PositionComponentInterceptor.class);

	PositionComponentInterceptor() {
		super(PositionComponent.class);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void triggerUpdateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is updated.");
		// TODO Auto-generated method stub
	}

	@Override
	public void triggerDeleteAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is deleted.");
		// TODO Auto-generated method stub
	}

	@Override
	public void triggerCreateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is created.");
		// TODO Auto-generated method stub
	}

}
