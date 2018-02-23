package net.bestia.entity.component.interceptor;

import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

public interface Interceptor {
	void interceptUpdate(EntityService entityService, Entity entity, Component component);

	void interceptCreated(EntityService entityService, Entity entity, Component component);

	void interceptDeleted(EntityService entityService, Entity entity, Component component);
}
