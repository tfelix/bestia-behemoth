package net.bestia.zoneserver.entity.factory

import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.zoneserver.inventory.InventoryService

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 *
 * @author Thomas Felix
 */
class PlayerBestiaEntityFactory(
    private val statusService: StatusService,
    private val inventoryService: InventoryService,
    private val playerBestiaDao: PlayerBestiaRepository
) : AbstractFactory<PlayerBestiaBlueprint>(PlayerBestiaBlueprint::class.java) {

  override fun performBuild(entity: Entity, blueprint: PlayerBestiaBlueprint) {
    val playerBestia = playerBestiaDao.findOneOrThrow(blueprint.playerBestiaId)

    entity.addAllComponents(
        listOf(
            PositionComponent(
                entityId = entity.id,
                shape = playerBestia.currentPosition
            ),
            VisualComponent(
                entityId = entity.id,
                visual = SpriteInfo.mob(playerBestia.origin.sprite)
            ),
            EquipComponent(
                entityId = entity.id
            ),
            InventoryComponent(
                entityId = entity.id
            ),
            PlayerComponent(
                entityId = entity.id,
                ownerAccountId = playerBestia.owner.id,
                playerBestiaId = playerBestia.id
            ),
            LevelComponent(
                entityId = entity.id
            ).also {
              it.level = playerBestia.level
              it.exp = playerBestia.exp
            },
            StatusComponent(
                entityId = entity.id,
                conditionValues = playerBestia.conditionValues,
                originalElement = playerBestia.origin.element
            ),
            TagComponent(
                entityId = entity.id,
                tags = setOf(TagComponent.PLAYER)
            )
        )
    )

    statusService.calculateStatusPoints(entity)
    inventoryService.updateMaxWeight(entity)
  }
}
