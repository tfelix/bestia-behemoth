package net.bestia.entity;

import java.util.Collection;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.PoisonPill;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.Interceptor;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * This is a special implementation of the {@link EntityService}. It wrappes
 * some method calls with zoneserver specific data. This is due to the problem
 * that the {@link EntityService} must also be available for the memory-db
 * module but there it lacks some specific dependencies of the zone server. This
 * is hence the workaround to allow implementation of these zoneserver specific
 * dependencies.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ZoneEntityService extends EntityService {
	
	private static final Logger LOG = LoggerFactory.getLogger(ZoneEntityService.class);

	private final Interceptor interceptor;
	private final EntityCache cache;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public ZoneEntityService(HazelcastInstance hz,
			ZoneAkkaApi akkaApi,
			Interceptor interceptor,
			EntityCache cache) {
		super(hz);

		Objects.requireNonNull(hz);

		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.interceptor = Objects.requireNonNull(interceptor);
		this.cache = Objects.requireNonNull(cache);
	}

	/**
	 * Returns a fresh entity which can be used inside the system. It already
	 * has a unique ID and can be used to persist date.
	 * 
	 * @return
	 */
	@Override
	public Entity newEntity() {

		Entity e = cache.getEntity();

		if (e == null) {
			LOG.debug("No recycled entity present. Creating new entity.");
			e = super.newEntity();
		}

		return e;
	}
	
	@Override
	public <T extends Component> T newComponent(Class<T> clazz) {
		
		final T addedComp = cache.getComponent(clazz);
		
		if(addedComp != null) {
			return addedComp;
		} else {
			return super.newComponent(clazz);
		}
	}

	/**
	 * Deletes the entity
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	@Override
	public void delete(Entity entity) {
		Objects.requireNonNull(entity);

		// Send message to kill off entity actor.
		akkaApi.sendEntityActor(entity.getId(), PoisonPill.getInstance());

		super.delete(entity);
		cache.stashEntity(entity);
	}

	/**
	 * Re-attaches an existing component to an entity. The component must not be
	 * owned by an entity (its entity id must be set to 0).
	 * 
	 * @param e
	 * @param addedComp
	 */
	@Override
	public void attachComponent(Entity e, Component comp) {
		super.attachComponent(e, comp);
		interceptor.interceptCreated(this, e, comp);
	}

	@Override
	public void attachComponents(Entity e, Collection<Component> attachComponents) {
		super.attachComponents(e, attachComponents);

		// After all is saved intercept the created components.
		attachComponents.forEach(c -> {
			interceptor.interceptCreated(this, e, c);
		});
	}

	/**
	 * Saves the given component back into the database. Update of the
	 * interceptors is called. If the component is not attached to an entity it
	 * throws an exception.
	 * 
	 * @param component
	 *            The component to be updated into the database.
	 */
	@Override
	public void updateComponent(Component component) {
		super.updateComponent(component);
		interceptor.interceptUpdate(this, getEntity(component.getEntityId()), component);
	}

	/**
	 * Only removes the component from the entity and the system. Does not yet
	 * safe the entity. This is to avoid multiple saves to the entity when
	 * removing bulk components. Important: CALL {@link #saveEntity(Entity)}
	 * after using this private method!
	 * 
	 * @param entity
	 * @param component
	 */
	@Override
	protected void prepareComponentRemove(Entity entity, Component component) {
		super.prepareComponentRemove(entity, component);

		interceptor.interceptDeleted(this, entity, component);
		cache.stashComponente(component);
	}
}
