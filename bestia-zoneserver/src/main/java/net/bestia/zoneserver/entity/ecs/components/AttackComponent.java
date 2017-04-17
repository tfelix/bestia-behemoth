package net.bestia.zoneserver.entity.ecs.components;

import java.util.List;

import net.bestia.model.battle.Damage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;

/**
 * Entities having this component can be participate in the attacking system.
 * 
 * @author Thomas Felix
 *
 */
public class AttackComponent extends Component {

	private static final long serialVersionUID = 1L;

	public AttackComponent(long id) {
		super(id);
		// no op
	}

	/**
	 * The level of the entity.
	 * 
	 * @return The level of the entity.
	 */
	int getLevel();

	/**
	 * {@link StatusPointsImpl}s of this entity. Please note that this status
	 * points might have been altered via items, equipments or status effects.
	 * The original status points without this effects applied can be obtained
	 * via {@link #getOriginalStatusPoints()}.
	 * 
	 * @return
	 */
	StatusPoints getStatusPoints();

	StatusPoints getOriginalStatusPoints();

	/**
	 * Adds a {@link StatusEffect} to the entity. This will possibly trigger
	 * effects associated with the adding of the effect.
	 * 
	 * @param effect
	 *            The effect to add.
	 */
	void addStatusEffect(StatusEffect effect);

	/**
	 * Removes the given status effect from the entity. This will possibly
	 * trigger effects associated with the removal of the effect.
	 * 
	 * @param effect
	 *            The effect to remove.
	 */
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
	 * @return The current element of the entity.
	 */
	Element getElement();

	/**
	 * The original element of this entity unaltered by status effects or
	 * equipments.
	 * 
	 * @return The original unaltered element.
	 */
	Element getOriginalElement();

	/**
	 * Returns a list with all available attacks.
	 * 
	 * @return A list of all available attacks.
	 */
	List<Attack> getAttacks();

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
	 * Checks the damage and reduces it by resistances or status effects.
	 * Returns the reduced damage the damage can be 0 if the damage was negated
	 * altogether. If there are effects which would be run out because of this
	 * damage then the checking will NOT run them out. It is only a check. Only
	 * applying the damage via {@link #takeDamage(Damage)} will trigger this
	 * removals.
	 * 
	 * @param damage
	 *            The damage to check if taken.
	 * @return Possibly reduced damage or NULL of it was negated completly.
	 */
	Damage checkDamage(Damage damage);

	/**
	 * Flag if the entity has bean killed. Important for the attack service in
	 * order to perform post death operations.
	 * 
	 * @return TRUE if the entity was killed. FALSE otherwise.
	 */
	boolean isDead();

	/**
	 * Returns the amount of EXP given if the entity was killed.
	 * 
	 * @return The amount of EXP given by this entity.
	 */
	int getKilledExp();
}
