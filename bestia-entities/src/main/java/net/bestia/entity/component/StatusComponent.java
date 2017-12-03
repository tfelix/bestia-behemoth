package net.bestia.entity.component;

import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

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
@ComponentActor("net.bestia.zoneserver.actor.entity.component.StatusComponentActor")
public class StatusComponent extends Component {

	private static final long serialVersionUID = 1L;

	/**
	 * Unmodified status points.
	 */
	@JsonProperty("osp")
	private StatusPoints originalStatusPoints = new StatusPointsImpl();
	
	@JsonProperty("sp")
	private StatusPoints statusPoints = new StatusPointsImpl();
	
	@JsonProperty("sbv")
	private StatusBasedValues statusBasedValues = new StatusBasedValuesImpl(statusPoints, 1);
	
	@JsonProperty("oe")
	private Element originalElement = Element.NORMAL;
	
	@JsonProperty("e")
	private Element element = Element.NORMAL;
	
	@JsonProperty("cv")
	private ConditionValues condValues = new ConditionValues();

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
		return condValues;
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
		condValues.set(statusValues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(element, originalElement, originalStatusPoints, statusBasedValues, statusPoints,
				condValues);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof StatusComponent)) {
			return false;
		}
		StatusComponent other = (StatusComponent) obj;
		if (element != other.element) {
			return false;
		}
		if (originalElement != other.originalElement) {
			return false;
		}
		if (!originalStatusPoints.equals(other.originalStatusPoints)) {
			return false;
		}
		if (!statusBasedValues.equals(other.statusBasedValues)) {
			return false;
		}
		if (!statusPoints.equals(other.statusPoints)) {
			return false;
		}
		if (!condValues.equals(other.condValues)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return String.format("StatusComponent[id: %d]", getId());
	}
}
