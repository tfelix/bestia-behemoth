package net.bestia.zoneserver.entity.component;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.domain.StatusValues;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.entity.StatusBasedValuesImpl;
import net.bestia.zoneserver.entity.StatusService;

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calulcations of the status values are non trivial it is important to use the
 * {@link StatusService} to access the values inside this component.
 * 
 * @author Thomas Felix
 *
 */
public class StatusComponent extends Component {

	private static final long serialVersionUID = 1L;

	/**
	 * Unmodified status points.
	 */
	private StatusPoints unmodifiedStatusPoints = new StatusPointsImpl();
	private StatusPoints statusPoints = new StatusPointsImpl();
	private StatusBasedValues statusBasedValues = new StatusBasedValuesImpl(statusPoints, 1);

	private Element unmodifiedElement = Element.NORMAL;
	private Element element = Element.NORMAL;

	// Current HP and Mana
	private StatusValues values = new StatusValues();

	public StatusComponent(long id, long entityId) {
		super(id, entityId);
		// no op
	}

	/**
	 * {@link StatusPointsImpl}s of this entity. Please note that this status
	 * points might have been altered via items, equipments or status effects.
	 * The original status points without this effects applied can be obtained
	 * via {@link #getUnmodifiedStatusPoints()}.
	 * 
	 * @return The current status points of the entity.
	 */
	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	public StatusPoints getUnmodifiedStatusPoints() {
		return unmodifiedStatusPoints;
	}

	/**
	 * Sets the status points of this component.
	 * 
	 * @param statusPoints
	 */
	public void setUnmodifiedStatusPoints(StatusPoints unmodifiedStatusPoints) {
		this.unmodifiedStatusPoints = unmodifiedStatusPoints;
	}

	public StatusBasedValues getStatusBasedValues() {
		return statusBasedValues;
	}

	public void setElement(Element element) {
		this.element = element;
	}

	public void setUnmodifiedElement(Element originalElement) {
		this.unmodifiedElement = originalElement;
	}

	/**
	 * The original element of this entity unaltered by status effects or
	 * equipments.
	 * 
	 * @return The original unaltered element.
	 */
	public Element getOriginalElement() {
		return unmodifiedElement;
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

	public StatusValues getValues() {
		return values;
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
