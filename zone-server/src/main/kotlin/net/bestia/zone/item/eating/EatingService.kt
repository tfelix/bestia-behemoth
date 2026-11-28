package net.bestia.zone.item.eating

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.meal.MealCatalog
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Eating a meal: a channel of [MealCatalog.channelSeconds] on the cast bar, then the meal's script.
 *
 * The meal stays in the bag while it is eaten, so a channel broken off costs nothing. Once through, the live bag
 * gives it up first and the database follows behind, keyed by the master, the way a bestia eating from its bag
 * always has; a meal the bag no longer holds by then - traded, dropped, eaten twice over - is simply not eaten.
 */
@Service
class EatingService(
  private val meals: MealCatalog,
  private val scripts: ItemScriptExecutionService,
  private val castCancelService: CastCancelService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val statusEffects: StatusEffectService,
) {

  fun isMeal(itemId: Long): Boolean = meals.isMeal(itemId)

  /**
   * Starts eating [playerItemId], breaking off whatever [entityId] was busy with unless [interrupt] is false - a
   * bestia's own planner only eats when idle. False when it holds no meal there.
   */
  fun begin(world: World, entityId: EntityId, playerItemId: Long, interrupt: Boolean = true): Boolean {
    val held = world.get(entityId, Inventory::class)?.byPlayerItemId(playerItemId) ?: return false
    if (!meals.isMeal(held.itemId)) return false

    if (interrupt) {
      castCancelService.interrupt(world, entityId)
    }
    world.add(entityId, Eating(playerItemId, held.itemId, meals.channelSeconds))

    return true
  }

  fun isEating(world: World, entityId: EntityId): Boolean = world.has(entityId, Eating::class)

  /** The channel is through: eat the meal, if the bag still holds it. Called with [Eating] already removed. */
  fun finish(world: World, entityId: EntityId, eating: Eating) {
    val held = world.get(entityId, Inventory::class)?.byPlayerItemId(eating.playerItemId)
    if (held == null || held.itemId != eating.itemId) {
      LOG.debug { "Entity $entityId finished eating ${eating.itemId} it no longer holds" }
      return
    }

    val script = scripts.scriptOf(eating.itemId) ?: return
    if (!scripts.use(world, entityId, script, eating.playerItemId, ScriptArgs.EMPTY)) {
      return
    }

    statusEffects.notifyActed(world, entityId, EntityAction.USED_ITEM)
    persistEaten(world, entityId, eating.itemId)
  }

  private fun persistEaten(world: World, entityId: EntityId, itemId: Long) {
    val owner = ItemOwner.of(world, entityId) ?: return
    asyncJobExecutor.submit(key = owner.masterId) {
      if (!inventoryService.consumeAll(owner.masterId, listOf(itemId to 1), owner.playerBestiaId)) {
        LOG.warn { "Entity $entityId ate item $itemId the database of master ${owner.masterId} no longer holds" }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
