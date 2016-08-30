package net.bestia.zoneserver.zone.entity.traits;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;

/**
 * Entities implementing this interface participating in the attacking system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Attackable {

	/**
	 * The level of the entity.
	 * 
	 * @return The level of the entity.
	 */
	int getLevel();

	/**
	 * {@link StatusPoints}s of this entity. Please note that this status points
	 * might have been altered via items, equipments or status effects. The
	 * original status points without this effects applied can be obtained via
	 * {@link #getOriginalStatusPoints()}.
	 * 
	 * @return
	 */
	StatusPoints getStatusPoints();

	StatusPoints getOriginalStatusPoints();

	void addStatusEffect(StatusEffect effect);

	void removeStatusEffect(StatusEffect effect);

	Element getElement();

	void kill();

}
