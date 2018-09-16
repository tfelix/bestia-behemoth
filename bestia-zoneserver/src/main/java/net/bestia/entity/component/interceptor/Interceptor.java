package net.bestia.entity.component.interceptor;

import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.Component;

public interface Interceptor {
	void interceptUpdate(EntityService entityService, Entity entity, Component component);

	void interceptCreated(EntityService entityService, Entity entity, Component component);

	void interceptDeleted(EntityService entityService, Entity entity, Component component);
}
