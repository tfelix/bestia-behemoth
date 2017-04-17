package net.bestia.zoneserver.entity.components;

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
	
	private int level;
	private StatusPoints statusPoints;
	private StatusPoints originalStatusPoints;

	private Element originalElement;
	private Element element;

	public AttackComponent(long id) {
		super(id);
		// no op
	}

	/**
	 * The level of the entity.
	 * 
	 * @return The level of the entity.
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * {@link StatusPointsImpl}s of this entity. Please note that this status
	 * points might have been altered via items, equipments or status effects.
	 * The original status points without this effects applied can be obtained
	 * via {@link #getOriginalStatusPoints()}.
	 * 
	 * @return
	 */
	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public StatusPoints getOriginalStatusPoints() {
		return originalStatusPoints;
	}

	/**
	 * Adds a {@link StatusEffect} to the entity. This will possibly trigger
	 * effects associated with the adding of the effect.
	 * 
	 * @param effect
	 *            The effect to add.
	 */
	//void addStatusEffect(StatusEffect effect);

	/**
	 * Removes the given status effect from the entity. This will possibly
	 * trigger effects associated with the removal of the effect.
	 * 
	 * @param effect
	 *            The effect to remove.
	 */
	//void removeStatusEffect(StatusEffect effect);

	/**
	 * Gets all currently added status effects. This list is immutable. Please
	 * use {@link #addStatusEffect(StatusEffect)} or
	 * {@link #removeStatusEffect(StatusEffect)} to alter this list indirectly.
	 * 
	 * @return The currently added status effects.
	 */
	//List<StatusEffect> getStatusEffects();

	/**
	 * The current element of this entity.
	 * 
	 * @return The current element of the entity.
	 */
	public Element getElement() {
		return element;
	}

	/**
	 * The original element of this entity unaltered by status effects or
	 * equipments.
	 * 
	 * @return The original unaltered element.
	 */
	public Element getOriginalElement() {
		return originalElement;
	}
}
