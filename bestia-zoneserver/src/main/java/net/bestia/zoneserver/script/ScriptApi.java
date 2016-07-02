package net.bestia.zoneserver.script;

import java.util.concurrent.Callable;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;

import net.bestia.zoneserver.ecs.component.Delay;
import net.bestia.zoneserver.ecs.component.ScriptCallable;
import net.bestia.zoneserver.ecs.entity.EntityBuilder;
import net.bestia.zoneserver.ecs.entity.EntityFactory;

/**
 * This class is a facade which wraps different calls to the ECS system. It is
 * also responsible for binding function calls into appropriate bindings to be
 * used inside of map scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptApi {

	private World world;
	private EntityFactory entityFactory;

	/**
	 * Setup the entity which calls the callable.
	 * 
	 * @param delay
	 * @param fn
	 */
	public void setTimer(int delay, Callable<Void> fn) {
		final Entity e = world.createEntity();	
		final EntityEdit ee = e.edit();
		
		ee.create(Delay.class).setDelay(delay);
		ee.create(ScriptCallable.class).fn = fn;
	}
	
	public EntityBuilder entity() {
		return new EntityBuilder(entityFactory);
	}

	public void initWorld(World world, EntityFactory entityFactory) {
		this.world = world;
		this.entityFactory = entityFactory;
	}

}
