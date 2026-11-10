package net.bestia.zone.breeding

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
import net.bestia.zone.breeding.ecs.BreedingFarmSystem
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.breeding.persistence.BreedingFarmRowRepository
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.SimulationAnchor
import net.bestia.zone.world.time.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** A Breeding Farm from sending a pair to taking the egg, against a real world and a real farm registry. */
class BreedingFarmFlowTest {

  private val clock = MovableClock(Instant.parse("2026-11-10T12:00:00Z"))

  private val structures = mockk<PlayerStructureRegistry> {
    every { of(FARM) } answers { farmEntry }
    every { of(neq(FARM)) } returns null
  }
  private var farmEntry: StructureEntry? = StructureEntry(FARM, StaticEntityKind.BREEDING_FARM, OWNER, ACCOUNT, Vec3L(0, 0, 0), 0f)

  private val async = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val rows = mockk<BreedingFarmRowRepository>(relaxed = true) {
    every { save(any()) } answers { firstArg() }
  }
  private val farms = BreedingFarms(rows, mockk<BestiaEggRepository>(), async)
  private val journeys = mockk<JourneyService>(relaxed = true) {
    every { start(any(), any(), any(), any(), any(), any(), any()) } returns null
  }
  private val itemTemplates = mockk<ItemTemplateRegistry> {
    every { idOf(BreedingFarmService.NESTING_BUNDLE) } returns BUNDLE
    every { idOf(BreedingFarmService.BESTIA_EGG) } returns EGG
    every { weightOf(EGG) } returns 200
  }
  private val inventoryService = mockk<InventoryService>(relaxed = true) {
    every { consumeAll(any(), any(), any()) } returns true
  }
  private val skills = mockk<BreederSkills> {
    every { bloodlineStudyLevel(any()) } returns 4
    every { beastfriendLevel(any()) } returns 0
  }
  private val laidEgg = EggSummary(EGG_INSTANCE, 1L, Sex.MALE, IndividualValues(), 1_000, 0)
  private val eggLaying = mockk<EggLaying> { every { lay(any(), any(), any()) } returns laidEgg }
  private val lootItems = mockk<LootItemEntitySpawner>(relaxed = true)

  private lateinit var world: EcsWorld
  private val worldView = mockk<WorldView> {
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(world) }
    every { modify<Any?>(any(), any()) } answers { secondArg<World.(EntityId) -> Any?>().invoke(world, firstArg()) }
  }

  private val service = BreedingFarmService(
    structures, farms, journeys, skills, itemTemplates, inventoryService, async, worldView, mockk(relaxed = true),
  )
  private val system = BreedingFarmSystem(
    structures, farms, service, mockk(relaxed = true), eggLaying, mockk { every { at(any()) } returns Season.SPRING },
    itemTemplates, lootItems, async, worldView, clock,
  )

  init {
    world = testWorld(systems = listOf(system))
  }

  private val owner = world.createEntity { id ->
    add(id, Master(masterId = OWNER, name = "Breeder"))
    add(id, Position.fromVec3(Vec3L(1, 0, 0)))
    add(id, Inventory(mutableListOf(Inventory.Item(itemId = BUNDLE, amount = 2, weight = 50))))
    add(id, CarryCapacity(current = 0, max = 10_000))
    add(id, KnownSkills(mutableMapOf()))
  }
  private val mother = givenBestia(MOTHER_BESTIA)
  private val father = givenBestia(FATHER_BESTIA)
  private val sexes = mapOf(mother to Sex.FEMALE, father to Sex.MALE)

  private fun givenBestia(playerBestiaId: Long, level: Int = 10, kind: BestiaKind = BestiaKind.BEAST): EntityId {
    return world.createEntity { id ->
      add(id, OwnedBestia(masterId = OWNER, playerBestiaId = playerBestiaId))
      add(id, Position.fromVec3(Vec3L(2, 0, 0)))
      add(id, Nature(Element.NORMAL, Size.SMALL, kind = kind))
      add(id, Level(level))
      add(id, SimulationAnchor())
    }
  }

  private fun start(): OpError? {
    return service.start(world, owner, FARM, mother, father, sexes)
  }

  private fun tick(seconds: Long = 2) {
    clock.advance(Duration.ofSeconds(seconds))
    world.tick(seconds.toFloat())
  }

  private fun bundles(): Int {
    return world.get(owner, Inventory::class)!!.getItems().filter { it.itemId == BUNDLE }.sumOf { it.amount }
  }

  @Test
  fun `a pair at the farm breeds once it is gathered and the bundle is used`() {
    assertNull(start())
    assertEquals(FarmPhase.GATHERING, farms.of(FARM).phase)
    assertEquals(1, bundles())

    tick()

    val farm = farms.of(FARM)
    assertEquals(FarmPhase.BREEDING, farm.phase)
    assertFalse(farm.hasBundle)
    assertEquals(FARM, world.get(mother, WorkAssignment::class)?.structureId)
    // Bloodline Study Lv. 4: 20% off, and a fourth dominant IV.
    assertEquals(BreedingTimes.reduced(BreedingTimes.breeding(10, 10), 20), farm.clutchTime)
    assertEquals(4, farm.dominantIvs)
  }

  @Test
  fun `when the timer ends the farm holds an egg, and taking it frees the farm but keeps the pair`() {
    start()
    tick()

    tick(farms.of(FARM).clutchTime.seconds + 1)

    assertEquals(FarmPhase.EGG_READY, farms.of(FARM).phase)
    assertEquals(laidEgg, farms.of(FARM).egg)

    assertNull(service.takeEgg(world, owner, FARM))

    assertEquals(FarmPhase.IDLE, farms.of(FARM).phase)
    assertTrue(world.get(owner, Inventory::class)!!.getItems().any { it.uniqueId == EGG_INSTANCE })
    verify { inventoryService.grantToMaster(OWNER, EGG, 1, uniqueId = EGG_INSTANCE) }
    assertEquals(mother, farms.of(FARM).pair?.motherEntityId)
  }

  @Test
  fun `a parent that dies before the egg is laid loses the clutch`() {
    start()
    tick()

    world.add(father, Dead())
    tick()

    val farm = farms.of(FARM)
    assertEquals(FarmPhase.IDLE, farm.phase)
    assertNull(farm.pair)
    assertFalse(farm.hasBundle)
    assertNull(world.get(mother, WorkAssignment::class))
  }

  @Test
  fun `a pair that cannot be bred is refused`() {
    val young = givenBestia(77L, level = 9)
    val bird = givenBestia(78L, kind = BestiaKind.BIRD)

    assertEquals(OpError.BREEDING_NOT_POSSIBLE, service.start(world, owner, FARM, mother, mother, sexes))
    assertEquals(OpError.BREEDING_NOT_POSSIBLE, service.start(world, owner, FARM, father, mother, sexes))
    assertEquals(OpError.BREEDING_NOT_POSSIBLE, service.start(world, owner, FARM, mother, young, sexes + (young to Sex.MALE)))
    assertEquals(OpError.BREEDING_NOT_POSSIBLE, service.start(world, owner, FARM, mother, bird, sexes + (bird to Sex.MALE)))
  }

  @Test
  fun `without a bundle nothing starts`() {
    world.get(owner, Inventory::class)!!.clearItems()

    assertEquals(OpError.BREEDING_NEEDS_BUNDLE, start())
    assertEquals(FarmPhase.IDLE, farms.of(FARM).phase)
  }

  @Test
  fun `an egg laid after the farm was knocked down is left on the ground`() {
    start()
    tick()
    every { eggLaying.lay(any(), any(), any()) } answers {
      farmEntry = null
      laidEgg
    }

    tick(farms.of(FARM).clutchTime.seconds + 1)

    verify { lootItems.spawnLootItem(any(), itemId = EGG, amount = 1, pos = Vec3L(0, 0, 0), uniqueId = EGG_INSTANCE) }
  }

  private companion object {
    const val FARM = 5L
    const val OWNER = 42L
    const val ACCOUNT = 4L
    const val BUNDLE = 440L
    const val EGG = 441L
    const val EGG_INSTANCE = 9_000L
    const val MOTHER_BESTIA = 11L
    const val FATHER_BESTIA = 12L
  }
}
