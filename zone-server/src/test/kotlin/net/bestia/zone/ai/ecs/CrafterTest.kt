package net.bestia.zone.ai.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.StarterKit
import net.bestia.zone.crafting.station.CrafterProfiles
import net.bestia.zone.crafting.station.StationWork
import net.bestia.zone.crafting.station.StationWorkSystem
import net.bestia.zone.crafting.station.StationWorkers
import net.bestia.zone.ecs.core.System
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.prop.CollectPropIntentSystem
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.QueueEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/** A bestia under CRAFT orders taking the stations of its master that wait for a free crafter. */
class CrafterTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun crafter(job: BestiaJob = BestiaJob.CRAFT, playerBestiaId: Long = 1L): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(job))
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = playerBestiaId))
    return id
  }

  private fun station(
    structureId: Long,
    at: Vec3L,
    takesAnyCrafter: Boolean = true,
    hasWork: Boolean = true,
    masterId: Long = MASTER,
    amount: Int = 2,
  ): StructureEntry {
    val queue = if (hasWork) listOf(QueueEntry(recipeId = RECIPE, amount = amount)) else emptyList()
    val entry = StructureEntry(
      structureId, StaticEntityKind.WORKBENCH, masterId, ownerAccountId = null, at, yaw = 0f, queue = queue,
      takesAnyCrafter = takesAnyCrafter,
    )
    ai.structures.add(entry)
    return entry
  }

  private fun workplaceOf(id: EntityId): Long? = ai.world.get(id, WorkAssignment::class)?.structureId

  private fun awaitWorking(crafter: EntityId, structureId: Long) {
    val station = ai.structures.single { it.id == structureId }
    ai.tickUntil(describe = { "it works at ${workplaceOf(crafter)}, standing at ${ai.positionOf(crafter)}" }) {
      workplaceOf(crafter) == structureId && ai.positionOf(crafter).distance(station.position) <= StationWorkers.WORK_RADIUS
    }
  }

  @Test
  fun `a crafter takes the nearest station that waits for one and goes to work there`() {
    val crafter = crafter()
    station(1L, Vec3L(12, 0, 0))
    station(2L, Vec3L(-6, 0, 0))

    awaitWorking(crafter, 2L)

    assertEquals(1L, ai.structures.single { it.id == 2L }.workerBestiaId)
  }

  @Test
  fun `a crafter leaves alone stations that do not wait for one`() {
    val crafter = crafter()
    station(1L, Vec3L(6, 0, 0), takesAnyCrafter = false)
    station(2L, Vec3L(-6, 0, 0), hasWork = false)
    station(3L, Vec3L(0, 6, 0), masterId = 99L)
    // The wanderer's area is 32 around home.
    station(4L, Vec3L(50, 0, 0))

    ai.tick(times = 20 * 20)

    assertNull(workplaceOf(crafter))
  }

  @Test
  fun `a bestia with another job takes no station`() {
    val gatherer = crafter(BestiaJob.REST)
    station(1L, Vec3L(6, 0, 0))

    ai.tick(times = 20 * 20)

    assertNull(workplaceOf(gatherer))
  }

  @Test
  fun `two crafters do not take the same station`() {
    val first = crafter(playerBestiaId = 1L)
    val second = crafter(playerBestiaId = 2L)
    station(1L, Vec3L(6, 0, 0))

    ai.tick(times = 20 * 20)

    assertEquals(1, listOf(first, second).count { workplaceOf(it) == 1L })
  }

  @Test
  fun `a crafter let go by its station moves on to the next one with work, and goes home without one`() {
    val crafter = crafter()
    station(1L, Vec3L(-6, 0, 0))
    awaitWorking(crafter, 1L)
    station(2L, Vec3L(12, 0, 0))

    // What the station does once its queue is done.
    ai.stationWorkers.unassign(ai.world, 1L)
    ai.structures.replaceAll { if (it.id == 1L) it.copy(queue = emptyList()) else it }

    awaitWorking(crafter, 2L)
    ai.stationWorkers.unassign(ai.world, 2L)
    assertEquals(home, ai.beliefOf(crafter, BestiaDomain.HOME_POSITION))
  }

  @Test
  fun `a crafter whose bag fills with products carries them to the chest and the station goes on`() {
    val crafter = crafter()
    ai.world.add(crafter, Inventory(mutableListOf()))
    // One product weighs 6, so a second one does not fit and the station pauses well below the haul load.
    ai.world.add(crafter, CarryCapacity(0, 10))
    station(1L, Vec3L(6, 0, 0), amount = 5)
    val chest = ai.spawnChest(Vec3L(8, 0, 0), structureId = 9L, masterId = MASTER)
    ai.world.registerSystems(listOf(AfterPickups(stationWork())))

    ai.tickUntil(maxTicks = 20 * 120, describe = { "the chest holds ${productsIn(chest)}" }) { productsIn(chest) >= 2 }
  }

  /** The fixture's pickups and the station both grant items; which runs first does not matter here. */
  private class AfterPickups(work: StationWorkSystem) : System by work {
    override val after: Set<KClass<out System>> = setOf(CollectPropIntentSystem::class)
  }

  private fun productsIn(id: EntityId): Int {
    return ai.world.getOrThrow(id, Inventory::class).getItems().filter { it.itemId == PRODUCT }.sumOf { it.amount }
  }

  /** The real station work, making one product a second into the worker's bag. */
  private fun stationWork(): StationWorkSystem {
    val recipe = Recipe(
      id = RECIPE, identifier = "STOOL", effect = RecipeEffect.PRODUCE, output = Recipe.ItemStack(PRODUCT, 1),
      inputs = emptyList(), requiredSkillId = 10L, requiredSkillLevel = 1, station = StaticEntityKind.WORKBENCH,
      craftSeconds = 1f, baseSuccessChance = 1f
    )
    val crafters = Crafters(StarterKit())
    val crafting = mockk<CraftingService>().also {
      every { it.craftSecondsFor(any(), any()) } returns 1f
      every { it.produceFromStation(any(), any(), any(), any()) } answers {
        ai.world.getOrThrow(secondArg<EntityId>(), Inventory::class).addItem(Inventory.Item(PRODUCT, 1, weight = 6))
        null
      }
    }
    val profiles = mockk<CrafterProfiles>().also {
      every { it.crafterFor(any(), any(), any()) } answers {
        crafters.Crafter(Master(MASTER, "Joiner"), 1L, null, null)
      }
    }
    val itemTemplates = mockk<ItemTemplateRegistry>().also {
      every { it.templateOf(PRODUCT) } returns ItemTemplateRegistry.Template(
        id = PRODUCT, baseLevel = 1, weight = 6, stackable = true, maxDurability = 0, material = ItemMaterial.WOOD
      )
    }

    return StationWorkSystem(
      structures = ai.structureRegistry,
      recipes = RecipeRegistry().apply { load(listOf(recipe)) },
      crafting = crafting,
      crafters = crafters,
      profiles = profiles,
      workers = ai.stationWorkers,
      itemTemplates = itemTemplates,
      connections = ConnectionInfoService(),
      work = StationWork(),
      states = mockk(relaxed = true),
      campfires = mockk(relaxed = true),
    )
  }

  private companion object {
    const val MASTER = 1L
    const val RECIPE = 1L
    const val PRODUCT = 4711L
  }
}
