package net.bestia.zoneserver.ecs.entity;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.messages.entity.SpriteType;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerItem;
import net.bestia.model.misc.Sprite.InteractionType;
import net.bestia.zoneserver.command.CommandContext;
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
@Wire
class ItemEntityFactory extends EntityFactory {

	public static final int ITEM_VANISH_DELAY = 48 * 60 * 60 * 1000; // 48h.

	private final Archetype itemArchetype;

	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<DelayedRemove> removeMapper;
	private ComponentMapper<net.bestia.zoneserver.ecs.component.Item> itemMapper;

	public ItemEntityFactory(World world, CommandContext ctx) {
		super(world, ctx);

		this.itemArchetype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(Position.class)
				.add(net.bestia.zoneserver.ecs.component.Item.class)
				.add(DelayedRemove.class)
				.build(world);

		world.inject(this);
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

		positionMapper.get(entityId).setPos(loc.x, loc.y);

		final Visible visible = visibleMapper.get(entityId);
		visible.sprite = item.getImage();

		final net.bestia.zoneserver.ecs.component.Item itemC = itemMapper.get(entityId);
		itemC.amount = amount;
		itemC.itemId = item.getId();
		itemC.playerItemId = -1; // no player item.

		// Remove after time out.
		removeMapper.get(entityId).removeDelay = ITEM_VANISH_DELAY;
	}

	/**
	 * Spawning a {@link PlayerItem} is needed if it is a EQUIPMENT type item
	 * which has additional information attached to it. Its reference inside the
	 * database must not be broken. Amount is always 1 since it can not stack.
	 * 
	 * @param loc
	 * @param item
	 */
	@Override
	public void spawn(EntityBuilder builder) {
		
		final int entityId = world.create(itemArchetype);

		positionMapper.get(entityId).setPos(builder.position.x, builder.position.y);

		final Visible visible = visibleMapper.get(entityId);
		visible.sprite = builder.sprite;
		visible.interactionType = InteractionType.ITEM;
		visible.spriteType = SpriteType.ITEM;

		final net.bestia.zoneserver.ecs.component.Item itemC = itemMapper.get(entityId);
		itemC.amount = builder.itemAmount;
		itemC.itemId = builder.itemId;
		itemC.playerItemId = builder.playerItemId;

		// Remove after time out.
		removeMapper.get(entityId).removeDelay = ITEM_VANISH_DELAY;
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		if(builder.itemId == 0) {
			return false;
		}
		if(builder.position == null) {
			return false;
		}
		if(builder.itemAmount < 1) {
			return false;
		}
		
		return true;
	}

}
