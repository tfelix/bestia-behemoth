package net.bestia.zoneserver.ecs.entity;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.messages.entity.SpriteType;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.entity.EntityBuilder.EntityType;
import net.bestia.zoneserver.zone.shape.Vector2;

@Wire
class BasicEntityFactory extends EntityFactory {

	private final Archetype archetype;

	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;

	public BasicEntityFactory(World world) {
		super(world);

		this.archetype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(Position.class)
				.build(world);

		world.inject(this);
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

	/**
	 * Generate a basic entity from the given builder object.
	 */
	@Override
	public void spawn(EntityBuilder builder) {
		
		final int entityId = world.create(archetype);
		
		final Visible visible = visibleMapper.get(entityId);
		
		visible.sprite = builder.sprite;
		visible.spriteType = SpriteType.STATIC;
		
		final Position position = positionMapper.get(entityId);
		
		position.setPos(builder.position.x, builder.position.y);
		
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		return builder.type == EntityType.BASIC;
	}
}
