package net.bestia.zoneserver.ecs.controller;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;

import net.bestia.model.domain.Item;
import net.bestia.zoneserver.ecs.component.DelayedRemove;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * This is an experimental approach to interface with the ECS. Lets see if this
 * works out. The Item controller is responsible for adding items to the ecs.
 * Items are active for 48h on the map.
 * 
 * @author Thomas
 *
 */
public class ItemController {

	public static final int ITEM_VANISH_DELAY = 48 * 60 * 60; // 48h.

	private final World world;
	private final Archetype itemArchetype;

	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<DelayedRemove> removeMapper;

	public ItemController(World world) {

		this.world = world;
		this.itemArchetype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(Position.class)
				.add(DelayedRemove.class)
				.build(world);

		this.positionMapper = world.getMapper(Position.class);
		this.visibleMapper = world.getMapper(Visible.class);
		this.removeMapper = world.getMapper(DelayedRemove.class);
	}

	/**
	 * Spawns an visible item on the map at the given coordinates. The stacked
	 * amount of this item will be 1.
	 * 
	 * @param loc
	 *            Location to spawn the item.
	 * @param item
	 *            The item to spawn.
	 */
	public void spawnItem(Vector2 loc, Item item) {
		spawnItem(loc, item, 1);
	}

	/**
	 * Spawns an visible item on the map at the given coordiantes. The items are
	 * stacked with the given amount.
	 * 
	 * @param loc
	 * @param item
	 * @param amount
	 */
	public void spawnItem(Vector2 loc, Item item, int amount) {
		final int entityId = world.create(itemArchetype);

		positionMapper.get(entityId).position = loc;
		final Visible visible = visibleMapper.get(entityId);
		visible.sprite = item.getImage();

		// Remove after time out.
		removeMapper.get(entityId).removeDelay = ITEM_VANISH_DELAY;
	}

}
