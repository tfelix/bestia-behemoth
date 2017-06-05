package net.bestia.zoneserver.entity.component;

import net.bestia.model.domain.Element;
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
	 * Sets the status points of this component.
	 * 
	 * @param statusPoints
	 */
	public void setOriginalStatusPoints(StatusPoints originalStatusPoints) {
		this.originalStatusPoints = originalStatusPoints;
	}

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
	 * The original element of this entity unaltered by status effects or
	 * equipments.
	 * 
	 * @return The original unaltered element.
	 */
	public Element getOriginalElement() {
		return originalElement;
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
	 * Sets new status values.
	 * 
	 * @param statusPoints
	 *            The new status values.
	 */
	public void setStatusPoints(StatusPoints statusPoints) {
		this.statusPoints = statusPoints;
	}
	
	/**
	 * Sets the status based values.
	 * 
	 * @param statusBasedValues
	 *            The new status based values.
	 */
	public void setStatusBasedValues(StatusBasedValues statusBasedValues) {
		this.statusBasedValues = statusBasedValues;
	}

	@Override
	public String toString() {
		return String.format("StatusComponent[]");
	}
}
