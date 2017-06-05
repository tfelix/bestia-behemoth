package net.bestia.zoneserver.entity.component;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.entity.StatusBasedValuesImpl;

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes.
 * 
 * @author Thomas Felix
 *
 */
public class StatusComponent extends Component {

	private static final long serialVersionUID = 1L;

	private StatusPoints originalStatusPoints = new StatusPointsImpl();
	private StatusPoints statusPoints = new StatusPointsImpl();
	private StatusBasedValues statusBasedValues = new StatusBasedValuesImpl(statusPoints, 1);

	private Element originalElement = Element.NORMAL;
	private Element element = Element.NORMAL;

	public StatusComponent(long id, long entityId) {
		super(id, entityId);
		// no op
	}

	/**
	 * {@link StatusPointsImpl}s of this entity. Please note that this status
	 * points might have been altered via items, equipments or status effects.
	 * The original status points without this effects applied can be obtained
	 * via {@link #getOriginalStatusPoints()}.
	 * 
	 * @return The current status points of the entity.
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
	// void addStatusEffect(StatusEffect effect);

	/**
	 * Removes the given status effect from the entity. This will possibly
	 * trigger effects associated with the removal of the effect.
	 * 
	 * @param effect
	 *            The effect to remove.
	 */
	// void removeStatusEffect(StatusEffect effect);

	/**
	 * Gets all currently added status effects. This list is immutable. Please
	 * use {@link #addStatusEffect(StatusEffect)} or
	 * {@link #removeStatusEffect(StatusEffect)} to alter this list indirectly.
	 * 
	 * @return The currently added status effects.
	 */
	// List<StatusEffect> getStatusEffects();

	public StatusBasedValues getStatusBasedValues() {
		return statusBasedValues;
	}

	public void setElement(Element element) {
		this.element = element;
	}

	public void setOriginalElement(Element originalElement) {
		this.originalElement = originalElement;
	}

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

	@Override
	public String toString() {
		return String.format("StatusComponent[]");
	}

	/**
	 * Sets the status points of this component.
	 * 
	 * @param statusPoints
	 */
	public void setOriginalStatusPoints(StatusPoints originalStatusPoints) {
		this.originalStatusPoints = originalStatusPoints;
	}

	/**
	 * Sets the status based values.
	 * 
	 * @param statusBasedValues
	 */
	public void setStatusBasedValues(StatusBasedValues statusBasedValues) {
		this.statusBasedValues = statusBasedValues;
	}

	public void setStatusPoints(StatusPoints statusPoints) {
		this.statusPoints = statusPoints;
	}
}
