package net.bestia.zoneserver.ecs.entity;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;

import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

public class BasicEntityFactory extends EntityFactory {

	private final Archetype archetype;

	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;

	public BasicEntityFactory(World world) {
		super(world);

		this.archetype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(Position.class)
				.build(world);

		this.positionMapper = world.getMapper(Position.class);
		this.visibleMapper = world.getMapper(Visible.class);

	}

	public void spawnBasicEntity(Vector2 pos, String sprite) {

	}

	/**
	 * Spawns the entity by a description file. The builder will be constructed
	 * by parsing this file.
	 * 
	 * @param pos
	 * @param descFileName
	 */
	public void spawnBasicEntityByDescription(Vector2 pos, String descFileName) {

	}

	@Override
	public void spawn(EntityBuilder builder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		// TODO Ersetzen.
		return false;
	}
}
