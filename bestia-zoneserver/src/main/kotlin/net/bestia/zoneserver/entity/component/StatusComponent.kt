package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.zoneserver.entity.component.receiver.InSameGuildReceiver
import net.bestia.zoneserver.entity.component.receiver.InSighReceiver
import net.bestia.zoneserver.entity.component.receiver.OwnerReceiver
import net.bestia.zoneserver.entity.component.transformer.StatusOnlyConditionTransformer
import net.bestia.model.domain.ConditionValues
import net.bestia.model.domain.Element
import net.bestia.model.domain.StatusPoints
import net.bestia.model.domain.StatusPointsImpl
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.entity.StatusBasedValuesImpl

import java.util.Objects

/**
 * Entities having this component can be participate in the attacking system. It
 * holds all data needed to perform status values changes. Since the
 * calculations of the status values are non trivial it is important to use the
 * [net.bestia.zoneserver.battle.StatusService] to access the values inside this component.
 *
 * @author Thomas Felix
 */
@ClientSync(
    directives = [
      ClientDirective(receiver = OwnerReceiver::class),
      ClientDirective(receiver = InSameGuildReceiver::class, transform = StatusOnlyConditionTransformer::class),
      ClientDirective(receiver = InSighReceiver::class, transform = StatusOnlyConditionTransformer::class)
    ]
)
data class StatusComponent(
    override val id: Long,
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
     * Sets the status based values.
     *
     * @param statusBasedValues The new status based values.
     */
    @get:JsonProperty("sbv")
    val statusBasedValues: StatusBasedValues = StatusBasedValuesImpl(statusPoints, 1),

    /**
     * The original element of this entity unaltered by status effects or
     * equipments.
     *
     * @return The original unaltered element.
     */
    @get:JsonProperty("oe")
    var originalElement: Element = Element.NORMAL,

    /**
     * The current element of this entity.
     *
     * @return The current element of the entity.
     */
    @get:JsonProperty("e")
    var element: Element = Element.NORMAL,

    @get:JsonProperty("cv")
    val conditionValues: ConditionValues = ConditionValues()
) : Component