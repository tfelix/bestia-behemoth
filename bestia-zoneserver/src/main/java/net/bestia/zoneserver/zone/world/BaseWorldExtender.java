package net.bestia.zoneserver.zone.world;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.PlayerManager;
import com.artemis.managers.TagManager;
import com.artemis.managers.UuidEntityManager;

import net.bestia.model.dao.MapEntityDAO;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.ActiveManager;
import net.bestia.zoneserver.ecs.manager.NetworkUpdateManager;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.ecs.manager.WorldPersistenceManager;
import net.bestia.zoneserver.ecs.system.AISystem;
import net.bestia.zoneserver.ecs.system.ActiveSpawnUpdateSystem;
import net.bestia.zoneserver.ecs.system.ChangedNetworkUpdateSystem;
import net.bestia.zoneserver.ecs.system.DelayedRemoveSystem;
import net.bestia.zoneserver.ecs.system.RegenerationSystem;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PersistSystem;
import net.bestia.zoneserver.ecs.system.VisibleSpawnUpdateSystem;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

public class BaseWorldExtender implements WorldExtend {

	@Override
	public void extend(World world, Map map, Zone zone) {
		// no op.
	}

	@Override
	public void configure(WorldConfiguration worldConfig, Map map, CommandContext ctx, Zone zone) {
		// Register all external helper objects.
		worldConfig.register(map);
		worldConfig.register(ctx);
		worldConfig.register(zone);
		worldConfig.register(ctx.getAccountRegistry());

		// Set all the systems.
		worldConfig.setSystem(new MovementSystem());
		worldConfig.setSystem(new AISystem());
		worldConfig.setSystem(new ActiveSpawnUpdateSystem());
		worldConfig.setSystem(new VisibleSpawnUpdateSystem());

		worldConfig.setSystem(new DelayedRemoveSystem());
		worldConfig.setSystem(new PersistSystem(10000));
		worldConfig.setSystem(new RegenerationSystem());
		// ChangedNetworkUpdateSystem must be last because it removes the
		// Changed component.
		worldConfig.setSystem(new ChangedNetworkUpdateSystem());
		// Set all the managers.
		worldConfig.setSystem(new PlayerBestiaSpawnManager(zone));
		worldConfig.setSystem(new ActiveManager());
		worldConfig.setSystem(new PlayerManager());
		worldConfig.setSystem(new TagManager());
		worldConfig.setSystem(new UuidEntityManager());
		worldConfig.setSystem(new NetworkUpdateManager());
		
		final MapEntityDAO mapEntityDao = ctx.getServiceLocator().getBean(MapEntityDAO.class);
		worldConfig.setSystem(new WorldPersistenceManager(map.getMapDbName(), mapEntityDao));
	}

}
