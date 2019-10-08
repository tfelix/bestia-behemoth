package net.bestia.zoneserver.status

import mu.KotlinLogging
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.OriginalStatusComponent
import net.bestia.zoneserver.entity.component.TagComponent
import org.springframework.stereotype.Component
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Component
class MobOriginalStatusComponentFactory(
    private val bestiaDao: BestiaRepository
) : OriginalStatusComponentFactory {
  override fun canBuildStatusFor(entity: Entity): Boolean {
    return entity.tryGetComponent(TagComponent::class.java)?.tags?.contains(TagComponent.MOB)
        ?: entity.tryGetComponent(MetaDataComponent::class.java)?.data?.get(MetaDataComponent.MOB_BESTIA_ID) != null
        ?: false
  }

  override fun buildComponent(entity: Entity): OriginalStatusComponent {
    val mobId = entity.getComponent(MetaDataComponent::class.java).data[MetaDataComponent.MOB_BESTIA_ID]?.toLong()
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

    return OriginalStatusComponent(
        entityId = entity.id,
        element = bestia.element,
        statusValues = statusValues
    )
  }
}