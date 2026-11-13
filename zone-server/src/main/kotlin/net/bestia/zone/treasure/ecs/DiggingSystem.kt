package net.bestia.zone.treasure.ecs

import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.casting.ecs.CastingSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.item.mining.PickaxeMining
import net.bestia.zone.item.mining.ecs.MiningSwing
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.treasure.DigService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Counts down each metre of digging and hands the finished one to [DigService], like `CraftingSystem` does. Pickaxe
 * swings count down here too: they start from the same click and write the same things.
 */
@SpringComponent
class DiggingSystem(
  private val digService: DigService,
  private val pickaxeMining: PickaxeMining,
) : System {
  override val phase = Phase.ACTIONS

  override val reads: ComponentClassSet = setOf(KnownSkills::class) + pickaxeMining.reads

  // A cast this tick has already decided whether it wears the tool or marks the status dirty.
  override val after = setOf(CastingSystem::class)

  /** A finished metre wears the tool, which can break it out of the hand, and an opened cache fills the bag. */
  override val writes: ComponentClassSet = setOf(
    Digging::class, MiningSwing::class, Equipment::class, Inventory::class, IsStatusValueDirty::class,
    CreateItemsIntent::class
  )

  override fun update(world: World, deltaTime: Float) {
    countDownDigging(world, deltaTime)
    countDownSwings(world, deltaTime)
  }

  private fun countDownDigging(world: World, deltaTime: Float) {
    var finished: MutableList<Pair<EntityId, Digging>>? = null

    world.query(Digging::class).each { id ->
      val digging = get<Digging>()
      digging.countdown(deltaTime)

      if (digging.hasElapsed()) {
        (finished ?: mutableListOf<Pair<EntityId, Digging>>().also { finished = it }).add(id to digging)
      }
    }

    finished?.forEach { (id, digging) ->
      world.remove(id, Digging::class)
      digService.finish(world, id, digging)
    }
  }

  private fun countDownSwings(world: World, deltaTime: Float) {
    var finished: MutableList<Pair<EntityId, MiningSwing>>? = null

    world.query(MiningSwing::class).each { id ->
      val swing = get<MiningSwing>()
      swing.countdown(deltaTime)

      if (swing.hasElapsed()) {
        (finished ?: mutableListOf<Pair<EntityId, MiningSwing>>().also { finished = it }).add(id to swing)
      }
    }

    finished?.forEach { (id, swing) ->
      world.remove(id, MiningSwing::class)
      pickaxeMining.finish(world, id, swing)
    }
  }
}
