package net.bestia.zoneserver.entity.component

import mu.KotlinLogging
import net.bestia.model.bestia.BasicDefense
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import org.springframework.stereotype.Component
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Component
class MobOriginalStatusComponentFactory(
    private val bestiaDao: BestiaRepository
) : ComponentFactory<StatusComponent> {

  override fun buildComponent(entity: Entity): StatusComponent {
    val mobId = entity.getComponent(MetadataComponent::class.java).data[MetadataComponent.MOB_BESTIA_ID]?.toLong()
        ?: throw IllegalStateException("MetaDataComponent did not contain MOB_BESTIA_ID")
    val bestia = bestiaDao.findOneOrThrow(mobId)
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

    LOG.trace { "Build mob '${bestia.databaseName}' status: $statusValues" }

    return StatusComponent(
        entityId = entity.id,
        element = bestia.element,
        statusValues = statusValues,
        defense = BasicDefense(
            magicDefense = 0, // TODO Add this defense values to the bestia
            physicalDefense = 0
        ),
        statusBasedValues = BasicStatusBasedValues(
            statusValues = statusValues,
            level = bestia.level
        )
    )
  }
}