package net.bestia.zoneserver.ecs.controller;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;

import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerItem;
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
	private final ComponentMapper<net.bestia.zoneserver.ecs.component.Item> itemMapper;

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
		this.itemMapper = world.getMapper(net.bestia.zoneserver.ecs.component.Item.class);
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
	 * Spawning a {@link PlayerItem} is needed if it is a EQUIPMENT type item
	 * which has additional information attached to it. Its reference inside the
	 * database must not be broken. Amount is always 1 since it can not stack.
	 * 
	 * @param loc
	 * @param item
	 */
	public void spawnItem(Vector2 loc, PlayerItem item) {
		final int entityId = world.create(itemArchetype);

		positionMapper.get(entityId).position = loc;

		final Visible visible = visibleMapper.get(entityId);
		visible.sprite = item.getItem().getImage();

		final net.bestia.zoneserver.ecs.component.Item itemC = itemMapper.get(entityId);
		itemC.amount = 1;
		itemC.itemId = item.getItem().getId();
		itemC.playerItemId = item.getId();

		// Remove after time out.
		removeMapper.get(entityId).removeDelay = ITEM_VANISH_DELAY;
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

		final net.bestia.zoneserver.ecs.component.Item itemC = itemMapper.get(entityId);
		itemC.amount = amount;
		itemC.itemId = item.getId();
		itemC.playerItemId = -1; // no player item.

		// Remove after time out.
		removeMapper.get(entityId).removeDelay = ITEM_VANISH_DELAY;
	}

}
