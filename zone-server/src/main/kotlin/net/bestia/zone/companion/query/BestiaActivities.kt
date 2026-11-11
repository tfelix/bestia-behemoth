package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer.Roster.Activity
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.station.StationPause
import net.bestia.zone.crafting.station.StationWork
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What a bestia in the world is doing now, for the companion app's status line. Read inside a world lease: the
 * structure registry and station progress belong to the tick thread.
 */
@Component
class BestiaActivities(
  private val structures: PlayerStructureRegistry,
  private val recipes: RecipeRegistry,
  private val items: ItemTemplateRegistry,
  private val work: StationWork,
) {

  fun of(world: World, entityId: EntityId, job: BestiaJob): Activity? {
    val agent = world.get(entityId, AiAgent::class) ?: return null
    val goal = agent.currentGoal?.name
    val workplace = world.get(entityId, WorkAssignment::class)?.let { structures.of(it.structureId) }

    // Self-care, fighting and hauling break off the work; anything else at a workplace is the work itself.
    val kind = GOAL_KINDS[goal]
    if (kind != null) return Activity(kind)
    if (workplace != null) return atWorkplace(workplace)

    return Activity(if (job == BestiaJob.GUARD) "GUARDING" else "IDLE")
  }

  private fun atWorkplace(station: StructureEntry): Activity {
    if (station.kind == StaticEntityKind.MINING_SHAFT) return Activity("MINING", station = station.kind.name)

    val entry = station.queue.firstOrNull()
    val output = entry?.let { recipes.of(it.recipeId)?.output }
    val pause = work.peek(station.id)?.pause?.takeIf { it != StationPause.NONE }

    return Activity(
      kind = "CRAFTING",
      item = output?.let { items.identifierOf(it.itemId) },
      done = entry?.done,
      total = entry?.amount,
      station = station.kind.name,
      pause = pause?.name,
    )
  }

  private companion object {
    val GOAL_KINDS = mapOf(
      "Gather" to "GATHERING",
      "HaulToChest" to "HAULING",
      "RestockFood" to "FETCHING_FOOD",
      "EatFromBag" to "EATING",
      "EatVegetation" to "EATING",
      "HealFromBag" to "HEALING",
      "KillAttacker" to "FIGHTING",
      "KillEnemy" to "FIGHTING",
      "Retreat" to "RETREATING",
      "RestAtHome" to "RESTING_AT_HOME",
      "Sleep" to "SLEEPING",
    )
  }
}
