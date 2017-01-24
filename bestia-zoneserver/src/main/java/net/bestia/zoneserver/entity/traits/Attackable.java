package net.bestia.zoneserver.entity.traits;

import java.util.List;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Damage;

/**
 * Entities implementing this interface participating in the attacking system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Attackable extends Entity {

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

	/**
	 * Gets all currently added status effects. This list is immutable. Please
	 * use {@link #addStatusEffect(StatusEffect)} or
	 * {@link #removeStatusEffect(StatusEffect)} to alter this list indirectly.
	 * 
	 * @return The currently added status effects.
	 */
	List<StatusEffect> getStatusEffects();

	/**
	 * The current element of this entity.
	 * 
	 * @return
	 */
	Element getElement();

	/**
	 * The original element of this entity unaltered by status effects or
	 * equipments.
	 * 
	 * @return
	 */
	Element getOriginalElement();

	/**
	 * Returns a list with all available attacks.
	 * 
	 * @return A list of all available attacks.
	 */
	List<Attack> getAttacks();

	/**
	 * Kills the entity.
	 */
	void kill();

	/**
	 * This will perform a check damage for reducing it and alter all possible
	 * status effects and then apply the damage to the entity. If its health
	 * sinks below 0 then the {@link #kill()} method will be triggered.
	 * 
	 * @param damage
	 *            The damage to apply to this entity.
	 * @return The reduced damage.
	 */
	Damage takeDamage(Damage damage);

	/**
	 * CHecks the damage and reduces it by resistances or status effects.
	 * Returns the reduced damage or null of the damage was negated altogether.
	 * If there are effects which would be run out because of this damage then
	 * the checking will NOT run them out. It is only a check. Only applying the
	 * damage via {@link #takeDamage(Damage)} will trigger this removals.
	 * 
	 * @param damage
	 *            The damage to check if taken.
	 * @return Possibly reduced damage or NULL of it was negated completly.
	 */
	Damage checkDamage(Damage damage);

	/**
	 * Applies this damage to the entity without checking it any further.
	 * 
	 * @param damage
	 *            The damage to apply without reduce.
	 */
	void takeTrueDamage(Damage damage);
}
