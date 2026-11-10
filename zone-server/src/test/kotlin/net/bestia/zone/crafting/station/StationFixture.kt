package net.bestia.zone.crafting.station

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.util.EntityId
import java.time.Instant

/** A workbench of [OWNER] with a stateful registry behind it, and one recipe of two planks for a chair. */
class StationFixture(stationKind: StaticEntityKind = StaticEntityKind.WORKBENCH, burnsOutAt: Instant? = null) {

  val world: EcsWorld = testWorld()

  val recipe = Recipe(
    id = RECIPE, identifier = "CHAIR", effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(CHAIR, 1), inputs = listOf(Recipe.ItemStack(PLANK, 2)),
    requiredSkillId = 10L, requiredSkillLevel = 1, station = stationKind, craftSeconds = 4f, baseSuccessChance = 1f
  )

  val recipes = RecipeRegistry().apply { load(listOf(recipe)) }

  var station = StructureEntry(STATION, stationKind, OWNER, ACCOUNT, Vec3L(0, 0, 0), yaw = 0f, burnsOutAt = burnsOutAt)

  /** Answers and records the way the real registry does, minus the database. */
  val structures = mockk<PlayerStructureRegistry>().also { registry ->
    every { registry.of(STATION) } answers { station }
    every { registry.all() } answers { listOf(station) }
    every { registry.setStationContents(STATION, any(), any()) } answers {
      station = station.copy(queue = secondArg(), held = thirdArg<Map<Long, Int>>().filterValues { it > 0 })
    }
  }

  val itemTemplates = mockk<ItemTemplateRegistry>().also { templates ->
    every { templates.templateOf(any()) } answers {
      ItemTemplateRegistry.Template(
        id = firstArg(), baseLevel = 1, weight = 10, stackable = true, maxDurability = 0, material = ItemMaterial.WOOD
      )
    }
  }

  /** The owner's master at [at] with [planks] in a bag that carries [maxWeight]. */
  fun givenOwner(planks: Int, at: Vec3L = Vec3L(1, 0, 0), maxWeight: Int = 1000, masterId: Long = OWNER): EntityId {
    return world.createEntity { id ->
      add(id, Master(masterId = masterId, name = "Joiner"))
      add(id, Account(ACCOUNT))
      add(id, Position.fromVec3(at))
      val items = if (planks > 0) mutableListOf(Inventory.Item(itemId = PLANK, amount = planks, weight = 10)) else mutableListOf()
      add(id, Inventory(items))
      add(id, CarryCapacity(current = 0, max = maxWeight))
    }
  }

  fun planksIn(entityId: EntityId): Int {
    return world.get(entityId, Inventory::class)!!.getItems().filter { it.itemId == PLANK }.sumOf { it.amount }
  }

  companion object {
    const val STATION = 7L
    const val OWNER = 42L
    const val ACCOUNT = 4L
    const val RECIPE = 1L
    const val PLANK = 50L
    const val CHAIR = 51L
  }
}
