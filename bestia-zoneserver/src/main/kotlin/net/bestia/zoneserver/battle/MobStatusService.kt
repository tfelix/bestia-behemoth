package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.*
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class MobStatusService(
    private val playerBestiaDao: PlayerBestiaRepository,
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
    val bestiaId = metaDataComponent?.tryGetAsLong(MetaDataComponent.MOB_BESTIA_ID)

    return calculateMobStatus(bestiaId, entity)
  }

  private fun calculateMobStatus(bestiaId: Long, entity: Entity): StatusComponent {
    val bestia = bestiaRepository.findOneOrThrow(bestiaId)
    calculateUnmodifiedStatusValues(
        bVals = bestia.baseValues
    )
  }

  /**
   * At first this calculates the unmodified, original status points.
   */
  private fun calculateUnmodifiedStatusValues(
      lv: Int,
      bVals: BaseValues,
      iVals: BaseValues = BaseValues.NULL_VALUES,
      eVals: BaseValues = BaseValues.NULL_VALUES
  ): StatusValues {
    val str = (bVals.strength * 2 + iVals.strength + eVals.strength / 4) * lv / 100 + 5
    val vit = (bVals.vitality * 2 + iVals.vitality + eVals.vitality / 4) * lv / 100 + 5
    val intel = (bVals.intelligence * 2 + iVals.intelligence + eVals.intelligence / 4) * lv / 100 + 5
    val will = (bVals.willpower * 2 + iVals.willpower + eVals.willpower / 4) * lv / 100 + 5
    val agi = (bVals.agility * 2 + iVals.agility + eVals.agility / 4) * lv / 100 + 5
    val dex = (bVals.dexterity * 2 + iVals.dexterity + eVals.dexterity / 4) * lv / 100 + 5

    return BasicStatusValues(
        str,
        vit,
        intel,
        will,
        agi,
        dex
    )
  }
}

