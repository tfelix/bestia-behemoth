package net.bestia.zoneserver.entity.component.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

/**
 * Evtl ist das obsolete da wird alles über die services abdecken können
 * sollten. vielleicht ist die lösung hier aber dennoch eleganter?
 * 
 * @author Thomas Felix
 *
 */
//@Component
public class PositionComponentInterceptor extends ComponentInterceptor<PositionComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(PositionComponentInterceptor.class);

	PositionComponentInterceptor() {
		super(PositionComponent.class);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is updated.");
		// TODO Auto-generated method stub
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is deleted.");
		// TODO Auto-generated method stub
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is created.");
		// TODO Auto-generated method stub
	}

}
