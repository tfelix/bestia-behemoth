package net.bestia.zoneserver.entity.component

import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.StatusBasedValues

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calculations of the status values are non trivial it is important to use the
 * [net.bestia.zoneserver.battle.MobStatusService] to access the values inside this component.
 *
 * @author Thomas Felix
 */
data class StatusComponent(
    override val entityId: Long,

    /**
     * [BasicStatusValues]s of this entity. Please note that this status
     * points might have been altered via items, equipments or status effects.
     * The original status points without this effects applied can be obtained
     * via originalStatusValues.
     *
     * @return The current status points of the entity.
     */
    val statusValues: StatusValues,

    /**
     * Sets the status based values.
     *
     * @param statusBasedValues The new status based values.
     */
    val statusBasedValues: StatusBasedValues,

    /**
     * The current element of this entity.
     *
     * @return The current element of the entity.
     */
    val element: Element = Element.NORMAL
) : Component