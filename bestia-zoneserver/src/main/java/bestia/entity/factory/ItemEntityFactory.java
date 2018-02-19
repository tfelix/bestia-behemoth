package bestia.entity.factory;

import bestia.entity.Entity;
import bestia.entity.component.*;
import bestia.entity.component.TagComponent.Tag;
import bestia.model.dao.ItemDAO;
import bestia.model.domain.Item;
import bestia.model.domain.SpriteInfo;
import bestia.model.geometry.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.Set;

/**
 * This factory can be used in order to create map item entities on which the player can click
 * and pick them up if necessary.
 */
@org.springframework.stereotype.Component
public class ItemEntityFactory {

	private static final Logger LOG = LoggerFactory.getLogger(ItemEntityFactory.class);

	private static final Blueprint ITEM_BLUEPRINT;

	static {
		Blueprint.Builder builder = new Blueprint.Builder();
		builder.addComponent(VisibleComponent.class)
				.addComponent(ItemComponent.class)
				.addComponent(PositionComponent.class)
				.addComponent(TagComponent.class)
				.addComponent(StatusComponent.class);

		ITEM_BLUEPRINT = builder.build();
	}

	private final EntityFactory entityFactory;
	private final ItemDAO itemDao;

	@Autowired
	public ItemEntityFactory(EntityFactory entityFactory, ItemDAO itemDao) {

		this.entityFactory = Objects.requireNonNull(entityFactory);
		this.itemDao = Objects.requireNonNull(itemDao);
	}

	/**
	 * Creates an dropped item entity on the given position with the name.
	 *
	 * @param itemDbName The item DB name of the item to spawn.
	 * @param position   Location where to spawn the item entity.
	 * @param amount     The amount of items to spawn at this location.
	 * @return The created entity.
	 */
	public Entity build(String itemDbName, Point position, int amount) {
		final Item item = itemDao.findItemByName(itemDbName);
		return build(item, position, amount);
	}

	public Entity build(Item item, Point position, int amount) {
		Objects.requireNonNull(item);
		Objects.requireNonNull(position);

		if (amount <= 0) {
			throw new IllegalArgumentException("Amount can not be 0 or negative.");
		}
		
		LOG.info("Spawning item: {}, amount: {} on {}.", item, amount, position);
		
		final PositionComponentSetter posSetter = new PositionComponentSetter(position);
		final VisibleComponentSetter visSetter = new VisibleComponentSetter(SpriteInfo.item(item.getImage()));
		final TagComponentSetter tagSetter = new TagComponentSetter(Tag.ITEM, Tag.PERSIST);
		final ItemStatusComponentSetter statusSetter = new ItemStatusComponentSetter(item);
		
		final Set<ComponentSetter<? extends Component>> compSetter = EntityFactory.makeSet(
				posSetter,
				visSetter,
				tagSetter,
				statusSetter);
		
		return entityFactory.buildEntity(ITEM_BLUEPRINT, compSetter);
	}
}
