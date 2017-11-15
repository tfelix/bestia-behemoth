package net.bestia.entity.component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Service to interact with the entities inventory component.
 * It will perform certain settings depending on which entity has the component attached.
 */
@Service
public class InventoryService {

	private final EntityService entityService;

	public InventoryService(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * If the entity also has a {@link StatusComponent} attached it will update the max weight
	 * setting depending on this status strength.
	 *
	 * @param entity This entity inventory gets updated with the maximum weight.
	 */
	public void updateMaxWeight(Entity entity) {
		final InventoryComponent invComp = checkAndGetInventoryComp(entity);

		// Now we must check if we have a status component.
		final Optional<StatusComponent> statusComp = entityService.getComponent(entity, StatusComponent.class);

		if (statusComp.isPresent()) {
			// Calculate the carriable weight.
			final int maxWeight = statusComp.get().getStatusPoints().getStrength() * 10 + 100;
			invComp.setMaxWeight(maxWeight);
		} else {
			invComp.setMaxWeight(0);
		}

		entityService.updateComponent(invComp);
	}

	/**
	 * The maximum number of items are either limited by weight of by a concrete number of max
	 * items. If this number is reached no more items can be added to the inventory.
	 * If this is set to another value then {@link InventoryComponent#UNLIMITED_ITEMS} then the weight is ignored.
	 *
	 * @param entity      The entity to update the max num item component.
	 * @param maxNumItems The maximum number of items which can be attached to this inventory component.
	 */
	public void setMaxItemCount(Entity entity, int maxNumItems) {
		final InventoryComponent invComp = checkAndGetInventoryComp(entity);
		invComp.setMaxItemCount(maxNumItems);
		entityService.updateComponent(invComp);
	}

	/**
	 * Checks if the entity has the inventory component, if not the component will be added.
	 */
	private InventoryComponent checkAndGetInventoryComp(Entity entity) {
		final Optional<InventoryComponent> invCompOpt = entityService.getComponent(entity, InventoryComponent.class);
		InventoryComponent invComp;
		if (!invCompOpt.isPresent()) {
			invComp = entityService.newComponent(InventoryComponent.class);
			entityService.attachComponent(entity, invComp);
		} else {
			invComp = invCompOpt.get();
		}

		return invComp;
	}
}
