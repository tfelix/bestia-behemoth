package net.bestia.entity.component;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.entity.StatusBasedValuesImpl;

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calculations of the status values are non trivial it is important to use the
 * {@link StatusService} to access the values inside this component.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.OWNER)
public class StatusComponent extends Component {

	private static final long serialVersionUID = 1L;

	/**
	 * Unmodified status points.
	 */
	private StatusPoints originalStatusPoints = new StatusPointsImpl();
	private StatusPoints statusPoints = new StatusPointsImpl();
	private StatusBasedValues statusBasedValues = new StatusBasedValuesImpl(statusPoints, 1);
	private Element originalElement = Element.NORMAL;
	private Element element = Element.NORMAL;
	private ConditionValues values = new ConditionValues();

	public StatusComponent(long id) {
		super(id);
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
	 *            The original, unmodified {@link StatusPoints}.
	 */
	void setOriginalStatusPoints(StatusPoints statusPoints) {
		this.originalStatusPoints = statusPoints;
	}

	public StatusBasedValues getStatusBasedValues() {
		return statusBasedValues;
	}

	void setElement(Element element) {
		this.element = element;
	}

	void setUnmodifiedElement(Element originalElement) {
		this.originalElement = originalElement;
	}

	public ConditionValues getConditionValues() {
		return values;
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
	void setStatusPoints(StatusPoints statusPoints) {
		this.statusPoints = statusPoints;
	}

	/**
	 * Sets the status based values.
	 * 
	 * @param statusBasedValues
	 *            The new status based values.
	 */
	void setStatusBasedValues(StatusBasedValues statusBasedValues) {
		this.statusBasedValues = statusBasedValues;
	}

	void setConditionValues(ConditionValues statusValues) {
		values.set(statusValues);
	}

	@Override
	public String toString() {
		return String.format("StatusComponent[id: %d]", getId());
	}
}
