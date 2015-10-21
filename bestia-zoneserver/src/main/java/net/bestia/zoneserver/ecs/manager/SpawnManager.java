package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.GroupManager;

import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.zone.spawn.Spawner;

/**
 * This manager keeps track about the dying NPC mob bestias. If the number is
 * below the number of the spawned bestias for this map a new spawning entity is
 * created.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class SpawnManager extends BaseEntitySystem {

	private GroupManager groupManager;
	private ComponentMapper<MobGroup> mobGroupMapper;

	private final Map<String, Spawner> spawners = new HashMap<>();

	public SpawnManager(List<Spawner> spawners) {
		super(Aspect.all(MobGroup.class));

		// Dont process this system. Only react passiv.
		setEnabled(false);

		for (Spawner sp : spawners) {
			this.spawners.put(sp.getMobName(), sp);
		}
	}

	@Override
	public void inserted(int e) {
		// no op.
	}

	@Override
	public void removed(int eId) {
		final Entity entity = world.getEntity(eId);
		
	}

	@Override
	protected void processSystem() {
		// is not called.
	}

}
