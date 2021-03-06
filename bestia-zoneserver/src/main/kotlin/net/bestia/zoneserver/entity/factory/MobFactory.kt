package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.MetadataComponent
import net.bestia.zoneserver.entity.component.MetadataComponent.Companion.MOB_BESTIA_ID
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
    private val bestiaDao: BestiaRepository,
    private val mobStatusComponentFactory: MobOriginalStatusComponentFactory,
    private val idGenerator: IdGenerator
) {

  fun build(mobDbName: String, pos: Vec3): Entity {
    val bestia = bestiaDao.findByDatabaseName(mobDbName)
        ?: run {
          LOG.warn { "Could not find mob '$mobDbName' in database" }
          throw IllegalArgumentException("Could not find mob '$mobDbName' in database")
        }

    val entityId = idGenerator.newId()
    val entity = Entity(entityId)

    val metaDataComponent = MetadataComponent(entity.id, mapOf(MOB_BESTIA_ID to bestia.id.toString()))
    entity.addComponent(metaDataComponent)
    val posComp = PositionComponent(
        entityId = entity.id,
        shape = pos,
        facing = Vec3(0, 1, 0),
        isSightBlocking = true
    )
    entity.addComponent(posComp)
    val visualComp = VisualComponent(
        entityId = entity.id,
        mesh = bestia.mesh
    )
    entity.addComponent(visualComp)
    val levelComp = LevelComponent(
        entityId = entity.id,
        level = bestia.level
    )
    entity.addComponent(levelComp)
    val equipComp = EquipComponent(entityId = entity.id)
    entity.addComponent(equipComp)
    entity.addComponent(InventoryComponent(entityId = entity.id))
    entity.addComponent(AiComponent(entityId = entity.id))
    entity.addComponent(mobStatusComponentFactory.buildComponent(entity))

    LOG.debug { "Created Entity(Mob): $bestia, at $pos" }

    return entity
  }
}
