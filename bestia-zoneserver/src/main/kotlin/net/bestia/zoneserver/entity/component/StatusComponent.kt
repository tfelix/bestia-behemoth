package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.StatusBasedValues

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

    @get:JsonProperty("osv")
    val originalStatusValues: StatusValues,

    @get:JsonProperty("ef")
    val effortValues: BaseValues = BaseValues.NULL_VALUES,

    /**
     * [BasicStatusValues]s of this entity. Please note that this status
     * points might have been altered via items, equipments or status effects.
     * The original status points without this effects applied can be obtained
     * via originalStatusValues.
     *
     * @return The current status points of the entity.
     */
    @get:JsonProperty("sv")
    val statusValues: StatusValues,

    /**
     * The original element of this entity unaltered by status effects or
     * equipments.
     *
     * @return The original unaltered element.
     */
    @get:JsonProperty("oe")
    val originalElement: Element = Element.NORMAL,

    @get:JsonProperty("cv")
    val conditionValues: ConditionValues,

    /**
     * Sets the status based values.
     *
     * @param statusBasedValues The new status based values.
     */
    @get:JsonProperty("sbv")
    val statusBasedValues: StatusBasedValues,

    /**
     * The current element of this entity.
     *
     * @return The current element of the entity.
     */
    @get:JsonProperty("e")
    var element: Element = Element.NORMAL
) : Component