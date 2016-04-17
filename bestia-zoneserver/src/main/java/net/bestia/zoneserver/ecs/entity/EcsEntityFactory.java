package net.bestia.zoneserver.ecs.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.artemis.World;

/**
 * This class capsules all factories for entity in the ECS system. So a calling
 * API only needs to use an entity builder to create a description of the entity
 * and the factory will invoke the correct building strategy.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EcsEntityFactory extends EntityFactory {

	public static final String ECS_ITEM_GROUP = "items";
	
	private List<EntityFactory> factories = new ArrayList<>();

	public EcsEntityFactory(World world) {
		super(world);

		factories.add(new ItemEntityFactory(world));
		factories.add(new NpcBestiaEntityFactory(world));
		factories.add(new PlayerBestiaEntityFactory(world));
		factories.add(new BasicEntityFactory(world));
	}

	@Override
	public void spawn(EntityBuilder builder) {
		final Optional<EntityFactory> fac = factories.stream().filter(x -> x.canSpawn(builder)).findFirst();

		if (fac.isPresent()) {
			fac.get().spawn(builder);
		}
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		return factories.stream().filter(x -> x.canSpawn(builder)).findAny().isPresent();
	}

}
