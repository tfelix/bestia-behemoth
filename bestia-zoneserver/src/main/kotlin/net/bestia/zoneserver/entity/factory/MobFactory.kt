package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.randomDirection
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.entity.IdGeneratorService
import net.bestia.zoneserver.entity.component.MetaDataComponent
import net.bestia.zoneserver.entity.component.MetaDataComponent.Companion.MOB_BESTIA_ID
import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException

private val LOG = KotlinLogging.logger { }

/**
 * Mob factory will create entities which serve as standard mobs for the bestia
 * system.
 *
 * @author Thomas Felix
 */
@Component
class MobFactory(
    private val statusService: StatusService,
    private val bestiaDao: BestiaRepository,
    private val idGenerator: IdGeneratorService
) {

  fun build(mobDbName: String, pos: Point): Entity {
    val bestia = bestiaDao.findByDatabaseName(mobDbName)
        ?: throw IllegalArgumentException("Could not find bestia in database for blueprint $mobDbName")

    LOG.info { "Create Entity(Mob): $bestia, at $pos." }

    val entityId = idGenerator.newId()
    val entity = Entity(entityId)

    val metaDataComponent = MetaDataComponent(entity.id, mapOf(MOB_BESTIA_ID to bestia.id.toString()))
    val posComp = PositionComponent(
        entityId = entity.id,
        shape = pos,
        facing = randomDirection(),
        isSightBlocking = true
    )
    val visualComp = VisualComponent(
        entityId = entity.id,
        visual = SpriteInfo.mob(bestia.sprite)
    )
    val levelComp = LevelComponent(
        entityId = entity.id,
        level = bestia.level
    )
    val equipComp = EquipComponent(entityId = entity.id)
    entity.addComponent(InventoryComponent(entityId = entity.id))
    entity.addComponent(AiComponent(entityId = entity.id))
    entity.addComponent(
        StatusComponent(
            entityId = entity.id,
            originalElement = bestia.element
        )
    )

    // TODO In createStatusPoint umbennenen und splitten zwischen neu-berechnen und erzeugen!
    val statusComp = statusService.calculateStatusPoints(entity)
    entity.addComponent(statusComp)

    return entity
  }
}
