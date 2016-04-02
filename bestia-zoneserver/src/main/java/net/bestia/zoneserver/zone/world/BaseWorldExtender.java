package net.bestia.zoneserver.zone.world;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.managers.PlayerManager;
import com.artemis.managers.TagManager;
import com.artemis.managers.UuidEntityManager;
import com.artemis.managers.WorldSerializationManager;

import net.bestia.model.dao.ZoneEntityDao;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.AccountRegistryManager;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.ecs.manager.WorldPersistenceManager;
import net.bestia.zoneserver.ecs.system.AISystem;
import net.bestia.zoneserver.ecs.system.ActiveSpawnUpdateSystem;
import net.bestia.zoneserver.ecs.system.DelayedRemoveSystem;
import net.bestia.zoneserver.ecs.system.RegenerationSystem;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PersistSystem;
import net.bestia.zoneserver.ecs.system.VisibleSpawnUpdateSystem;
import net.bestia.zoneserver.zone.Zone;
import net.bestia.zoneserver.zone.map.Map;

public class BaseWorldExtender implements WorldExtend {
	
	final WorldSerializationManager serializationManager = new WorldSerializationManager();

	@Override
	public void extend(World world, Map map, Zone zone) {
		serializationManager.setSerializer(new JsonArtemisSerializer(world));
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
		// Set all the managers.
		worldConfig.setSystem(new PlayerBestiaSpawnManager(zone));
		worldConfig.setSystem(new AccountRegistryManager());
		worldConfig.setSystem(new PlayerManager());
		worldConfig.setSystem(new TagManager());
		worldConfig.setSystem(new UuidEntityManager());
		
		// Prepare for serialization.
		worldConfig.setSystem(serializationManager);
		
		
		final ZoneEntityDao mapEntityDao = ctx.getServiceLocator().getBean(ZoneEntityDao.class);
		worldConfig.setSystem(new WorldPersistenceManager(map.getMapDbName(), mapEntityDao));
	}

}
