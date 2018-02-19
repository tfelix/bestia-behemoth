package bestia.entity.component.interceptor;

import bestia.entity.Entity;
import bestia.entity.EntityService;
import bestia.entity.component.Component;

public interface Interceptor {
	void interceptUpdate(EntityService entityService, Entity entity, Component component);

	void interceptCreated(EntityService entityService, Entity entity, Component component);

	void interceptDeleted(EntityService entityService, Entity entity, Component component);
}
