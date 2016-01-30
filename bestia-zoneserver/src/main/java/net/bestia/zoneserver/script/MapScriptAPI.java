package net.bestia.zoneserver.script;

import java.util.concurrent.Callable;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;

import net.bestia.model.misc.Damage.DamageType;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Damage;
import net.bestia.zoneserver.ecs.component.Delay;
import net.bestia.zoneserver.ecs.component.ScriptCallable;

/**
 * This class is a facade which wraps different calls to the ECS system. It is
 * also responsible for binding function calls into appropriate bindings to be
 * used inside of map scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScriptAPI {

	private World world;
	private CommandContext ctx;

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
	
	public void spawnDamage(int value, String uuid) {
		final EntityEdit ee = world.createEntity().edit();
		final Damage dmg = ee.create(Damage.class);
		dmg.damage = new net.bestia.model.misc.Damage(uuid, value, DamageType.HIT);
	}

	public void initWorld(World world) {
		this.world = world;
	}

	public void initContext(CommandContext ctx) {
		this.ctx = ctx;
	}
}
