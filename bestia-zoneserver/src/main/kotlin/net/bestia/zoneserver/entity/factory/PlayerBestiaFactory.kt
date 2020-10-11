package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 *
 * @author Thomas Felix
 */
@Component
class PlayerBestiaFactory(
    private val playerBestiaDao: PlayerBestiaRepository,
    private val statusComponentFactory: PlayerStatusComponentFactory,
    idGenerator: IdGenerator
) : EntityFactory(idGenerator) {

  fun build(playerBestiaId: Long): Entity {
    LOG.trace { "Spawning Player Bestia: $playerBestiaId" }

    val playerBestia = playerBestiaDao.findOneOrThrow(playerBestiaId)

    val entity = newEntity()
    val components = listOf(
        PositionComponent(
            entityId = entity.id,
            shape = playerBestia.currentPosition
        ),
        VisualComponent(
            entityId = entity.id,
            mesh = playerBestia.origin.mesh
        ),
        EquipComponent(
            entityId = entity.id
        ),
        InventoryComponent(
            entityId = entity.id
        ),
        MetadataComponent(
            entityId = entity.id,
            data = mapOf(
                MetadataComponent.MOB_PLAYER_BESTIA_ID to playerBestia.id,
                MetadataComponent.MOB_BESTIA_ID to playerBestia.origin.id
            ).mapValues { it.value.toString() }
        ),
        OwnerComponent(
            entityId = entity.id,
            ownerAccountIds = setOf(playerBestia.owner.id)
        ),
        LevelComponent(
            entityId = entity.id,
            level = playerBestia.level,
            exp = playerBestia.exp
        )
    )
    entity.addAllComponents(components)

    val status = statusComponentFactory.buildComponent(entity)

    entity.addComponent(status)

    LOG.debug { "Build PlayerBestia: $entity" }

    return entity
  }
}
