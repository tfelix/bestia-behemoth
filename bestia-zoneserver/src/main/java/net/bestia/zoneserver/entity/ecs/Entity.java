package net.bestia.zoneserver.entity.ecs;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.util.PackageLoader;

public class Entity implements Serializable {

	private static Logger LOG = LoggerFactory.getLogger(Entity.class);

	private static final long serialVersionUID = 1L;
	private static final int MAX_COMPONENT_ID;
	static {
		// we need to fing the max component id.
		PackageLoader<Component> loader = new PackageLoader<>(Component.class, "net.bestia.zoneserver.entity.ecs");

		int maxId = 0;
		for (Class<? extends Component> clazz : loader.getSubClasses()) {
			try {
				Method idMethod = clazz.getDeclaredMethod("getComponentId");
				Integer id = (Integer) idMethod.invoke(null);

				if (id > maxId) {
					maxId = id;
				}
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				LOG.error("Could not get the ID of the component.", e);
			}
		}

		MAX_COMPONENT_ID = maxId;
	}

	private long id = -1;
	private Component[] components = new Component[MAX_COMPONENT_ID];

	/**
	 * This will set an id of -1.
	 */
	public Entity(long id) {
		this.id = id;
	}

	public long getId() {
		return id;
	}
	
	public <T extends Component> T getComponent(int compId, Class<T> type) {
		return type.cast(components[compId]);
	}
}
