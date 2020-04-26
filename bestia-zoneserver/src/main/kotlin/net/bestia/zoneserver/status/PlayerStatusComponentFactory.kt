package net.bestia.zoneserver.status

import mu.KotlinLogging
import net.bestia.model.bestia.*
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.MetadataComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class PlayerStatusComponentFactory(
    private val playerBestiaDao: PlayerBestiaRepository
) : StatusComponentFactory {

  override fun canBuildStatusFor(entity: Entity): Boolean {
    return entity.tryGetComponent(MetadataComponent::class.java)
        ?.containsKey(MetadataComponent.MOB_PLAYER_BESTIA_ID)
        ?: false
  }

  override fun buildComponent(entity: Entity): StatusComponent {
    LOG.trace("Calculate status points for player entity {}.", entity)
    val playerBestiaId = entity.getComponent(MetadataComponent::class.java).tryGetAsLong(MetadataComponent.MOB_PLAYER_BESTIA_ID)
        ?: error("MetadataComponent did not contain MOB_PLAYER_BESTIA_ID")
    val levelComp = entity.getComponent(LevelComponent::class.java)

    val pb = playerBestiaDao.findOneOrThrow(playerBestiaId)

    val lv = levelComp.level
    val bVals = pb.baseValues
    val eVals = pb.effortValues
    val iVals = pb.individualValue

    val statusValues = calculateUnmodifiedStatusValues(lv, bVals, iVals, eVals)

    return StatusComponent(
        entityId = entity.id,
        statusValues = statusValues,
        element = pb.origin.element,
        defense = BasicDefense(
            magicDefense = 0, // TODO Add this defense values to the bestia
            physicalDefense = 0
        ),
        statusBasedValues = BasicStatusBasedValues(
            statusValues = statusValues,
            level = lv
        )
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