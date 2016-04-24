package net.bestia.zoneserver.ecs.entity;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.messages.entity.SpriteType;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.ScriptEntityTicker;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.entity.EntityBuilder.EntityType;
import net.bestia.zoneserver.ecs.manager.UuidManager;

@Wire
class BasicEntityFactory extends EntityFactory {
	
	public class EntityProxy {
		
		private World world;
		private int entityId;
		
		public EntityProxy(World world, int entityId) {
			this.world = world;
			this.entityId = entityId;
		}
		
		public void kill() {
			world.delete(entityId);
		}
	}

	private final Archetype archetype;

	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<ScriptEntityTicker> scriptTickerMapper;
	
	@Wire
	private UuidManager uuidManager;

	public BasicEntityFactory(World world, CommandContext ctx) {
		super(world, ctx);

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
	/*
	public void spawnBasicEntityByDescription(Vector2 pos, String descFileName) {

	}*/

	/**
	 * Generate a basic entity from the given builder object.
	 */
	@Override
	public void spawn(EntityBuilder builder) {
		
		final int entityId = world.create(archetype);
		
		final Visible visible = visibleMapper.get(entityId);
		
		visible.sprite = builder.sprite;
		visible.spriteType = SpriteType.STATIC;
		
		final EntityProxy prox = new EntityProxy(world, entityId);
		
		final Position position = positionMapper.get(entityId);	
		position.setPos(builder.position.x, builder.position.y);
		
		if(builder.tickCallback != null && builder.tickDelay > 0) {
			final EntityEdit edit = world.edit(entityId);
			edit.create(ScriptEntityTicker.class);
			
			// Config it.
			final ScriptEntityTicker ticker = scriptTickerMapper.get(entityId);
			ticker.interval = builder.tickDelay;
			ticker.cooldown = builder.tickDelay;
			ticker.fn = builder.tickCallback;
			ticker.fn.setDelegate(prox);
		}
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		// TODO Das hier ausbauen.
		return builder.type == EntityType.BASIC;
	}
}
