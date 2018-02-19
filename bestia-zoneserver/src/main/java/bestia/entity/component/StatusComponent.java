package bestia.entity.component;

import bestia.model.domain.Element;
import bestia.model.domain.StatusPoints;
import bestia.model.domain.StatusPointsImpl;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.model.domain.ConditionValues;
import bestia.model.entity.StatusBasedValues;
import bestia.model.entity.StatusBasedValuesImpl;

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calculations of the status values are non trivial it is important to use the
 * {@link bestia.zoneserver.battle.StatusService} to access the values inside this component.
 *
 * @author Thomas Felix
 */
@ComponentSync(SyncType.OWNER)
@ComponentActor("net.bestia.zoneserver.actor.entity.component.StatusComponentActor")
public class StatusComponent extends Component {

  private static final long serialVersionUID = 1L;

  /**
   * Original, unmodified status points.
   */
  private StatusPoints originalStatusPoints = new StatusPointsImpl();
  private StatusPoints statusPoints = new StatusPointsImpl();
  private StatusBasedValues statusBasedValues = new StatusBasedValuesImpl(statusPoints, 1);
  private Element originalElement = Element.NORMAL;
  private Element element = Element.NORMAL;

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
  @JsonProperty("sp")
  public StatusPoints getStatusPoints() {
    return statusPoints;
  }

  @JsonProperty("osp")
  public StatusPoints getOriginalStatusPoints() {
    return originalStatusPoints;
  }

  /**
   * Sets the status points of this component.
   *
   * @param statusPoints The original, unmodified {@link StatusPoints}.
   */
  public void setOriginalStatusPoints(StatusPoints statusPoints) {
    this.originalStatusPoints = statusPoints;
  }

  @JsonProperty("sbv")
  public StatusBasedValues getStatusBasedValues() {
    return statusBasedValues;
  }

  public void setElement(Element element) {
    this.element = element;
  }

  public void setUnmodifiedElement(Element originalElement) {
    this.originalElement = originalElement;
  }

  @JsonProperty("cv")
  public ConditionValues getConditionValues() {
    return condValues;
  }

  /**
   * The original element of this entity unaltered by status effects or
   * equipments.
   *
   * @return The original unaltered element.
   */
  @JsonProperty("oe")
  public Element getOriginalElement() {
    return originalElement;
  }

  /**
   * The current element of this entity.
   *
   * @return The current element of the entity.
   */
  @JsonProperty("e")
  public Element getElement() {
    return element;
  }

  /**
   * Sets new status values.
   *
   * @param statusPoints The new status values.
   */
  public void setStatusPoints(StatusPoints statusPoints) {
    this.statusPoints = statusPoints;
  }

  /**
   * Sets the status based values.
   *
   * @param statusBasedValues The new status based values.
   */
  public void setStatusBasedValues(StatusBasedValues statusBasedValues) {
    this.statusBasedValues = statusBasedValues;
  }

  void setConditionValues(ConditionValues statusValues) {
    condValues.set(statusValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(element,
            originalElement,
            originalStatusPoints,
            statusBasedValues,
            statusPoints,
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
    final StatusComponent other = (StatusComponent) obj;
    return element == other.element &&
            originalElement == other.originalElement &&
            originalStatusPoints.equals(other.originalStatusPoints) &&
            statusBasedValues.equals(other.statusBasedValues) &&
            statusPoints.equals(other.statusPoints) &&
            condValues.equals(other.condValues);
  }

  @Override
  public String toString() {
    return String.format("StatusComponent[id: %d]", getId());
  }
}
