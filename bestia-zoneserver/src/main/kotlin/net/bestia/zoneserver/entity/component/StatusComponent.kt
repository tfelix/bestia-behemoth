package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.domain.*
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.entity.StatusBasedValuesImpl

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calculations of the status values are non trivial it is important to use the
 * [net.bestia.zoneserver.battle.StatusService] to access the values inside this component.
 *
 * @author Thomas Felix
 */
data class StatusComponent(
    override val entityId: Long,

    @get:JsonProperty("osp")
    val originalStatusPoints: StatusPoints = StatusPointsImpl(),

    /**
     * [StatusPointsImpl]s of this entity. Please note that this status
     * points might have been altered via items, equipments or status effects.
     * The original status points without this effects applied can be obtained
     * via [.getOriginalStatusPoints].
     *
     * @return The current status points of the entity.
     */
    @get:JsonProperty("sp")
    val statusPoints: StatusPoints = StatusPointsImpl(),

    /**
     * The original element of this entity unaltered by status effects or
     * equipments.
     *
     * @return The original unaltered element.
     */
    @get:JsonProperty("oe")
    var originalElement: Element = Element.NORMAL,

    @get:JsonProperty("cv")
    val conditionValues: ConditionValues = ConditionValues()
) : Component {

  /**
   * Sets the status based values.
   *
   * @param statusBasedValues The new status based values.
   */
  @get:JsonProperty("sbv")
  val statusBasedValues: StatusBasedValues = StatusBasedValuesImpl(statusPoints, 1)

  /**
   * The current element of this entity.
   *
   * @return The current element of the entity.
   */
  @get:JsonProperty("e")
  var element: Element = Element.NORMAL
}