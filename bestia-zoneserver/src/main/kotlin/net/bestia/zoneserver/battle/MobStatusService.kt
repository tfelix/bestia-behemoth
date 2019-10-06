package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.*
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Service
class MobStatusService(
    private val bestiaRepository: BestiaRepository
) : StatusService {

  override fun createsStatusFor(entity: Entity): Boolean {
    val metaDataComponent = entity.tryGetComponent(MetaDataComponent::class.java)

    return metaDataComponent?.tryGetAsLong(MetaDataComponent.MOB_BESTIA_ID) != null
  }

  /**
   * Trigger the status point calculation. If some preconditions of status
   * calculation have changed recalculate the status for this given entity.
   * The entity must own a [StatusComponent].
   * Calculates and sets the modified status points based on the equipment and
   * or status effects. Each status effect can possibly return a modifier
   * which will then modify the original base status values.
   *
   * @param entity
   * The entity to recalculate the status.
   */
  override fun calculateStatusPoints(entity: Entity): StatusComponent {
    LOG.trace("Calculate status points for entity {}.", entity)

    val metaDataComponent = entity.tryGetComponent(MetaDataComponent::class.java)
    val statusComp = entity.getComponent(StatusComponent::class.java)
    val bestiaId = metaDataComponent?.tryGetAsLong(MetaDataComponent.MOB_BESTIA_ID)
        ?: throw IllegalStateException("Can not calcuate StatusPoints. Mob entity not associated with mob.")

    val bestia = bestiaRepository.findOneOrThrow(bestiaId)
    val bVals = bestia.baseValues
    val lv = bestia.level

    val str = (bVals.strength * 2) * lv / 100 + 5
    val vit = (bVals.vitality * 2) * lv / 100 + 5
    val intel = (bVals.intelligence * 2) * lv / 100 + 5
    val will = (bVals.willpower * 2) * lv / 100 + 5
    val agi = (bVals.agility * 2) * lv / 100 + 5
    val dex = (bVals.dexterity * 2) * lv / 100 + 5

    val statusValues = BasicStatusValues(
        str,
        vit,
        intel,
        will,
        agi,
        dex
    )

    return statusComp.copy(statusValues = statusValues)
  }
}

