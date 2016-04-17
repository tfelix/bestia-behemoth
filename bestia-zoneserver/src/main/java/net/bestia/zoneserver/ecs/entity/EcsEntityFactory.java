package net.bestia.zoneserver.ecs.entity;

import java.util.ArrayList;
import java.util.List;

import com.artemis.World;

import net.bestia.zoneserver.util.PackageLoader;

/**
 * This class capsules all factories for entity in the ECS system. So a calling
 * API only needs to use an entity builder to create a description of the entity
 * and the factory will invoke the correct building strategy.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EcsEntityFactory extends EntityFactory {
	
	private List<EntityFactory> factories = new ArrayList<>();

	public EcsEntityFactory(World world) {
		super(world);
		
		factories.add(new ItemEntityFactory(world));
		factories.add(new NpcBestiaEntityFactory2("test", world));
	}

	@Override
	public void spawn(EntityBuilder builder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		// TODO Auto-generated method stub
		return false;
	}

}
