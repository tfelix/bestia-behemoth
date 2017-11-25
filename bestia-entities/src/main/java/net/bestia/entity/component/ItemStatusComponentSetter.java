package net.bestia.entity.component;

import java.util.Objects;

import net.bestia.model.domain.Item;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;

/**
 * Items have a lookup table for their status values. Their status values are
 * dependent upon their level. Special values are changed 
 * 
 * @author Thomas Felix
 *
 */
public class ItemStatusComponentSetter extends ComponentSetter<StatusComponent> {

	private final Item item;

	/**
	 * Prepare the setter to work with the given item. It calculates the initial
	 * status values for this item. Usually a item status is fixed thus there is
	 * no need for using a service.
	 * 
	 * @param item
	 *            The item to derive the status from.
	 */
	public ItemStatusComponentSetter(Item item) {
		super(StatusComponent.class);

		this.item = Objects.requireNonNull(item);
	}

	@Override
	protected void performSetting(StatusComponent comp) {
		// FIXME Hier entsprechend eine korrekte status berechnung durchführen.

		final StatusPoints sp = new StatusPointsImpl();
		sp.setAgility(1);
		sp.setDexterity(1);
		sp.setIntelligence(1);
		sp.setStrenght(1);
		sp.setWillpower(1);

		sp.setDefense(10);
		sp.setMagicDefense(10);
		sp.setVitality(10);

		comp.setStatusPoints(sp);
	}

}
