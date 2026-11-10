package net.bestia.zone.breeding

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.OwnedBestiaPolicyViolationException
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
import net.bestia.zone.breeding.ecs.IncubatorSystem
import net.bestia.zone.breeding.persistence.BestiaEgg
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.breeding.persistence.IncubatorRowRepository
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.master.bestia.OwnedBestiasPublisher
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Optional

/** An Incubator from putting an egg in to the hatchling, against a real world and a real incubator registry. */
class IncubatorFlowTest {

  private val clock = MovableClock(Instant.parse("2026-11-10T12:00:00Z"))

  private val structures = mockk<PlayerStructureRegistry> {
    every { of(INCUBATOR) } returns StructureEntry(INCUBATOR, StaticEntityKind.INCUBATOR, OWNER, ACCOUNT, Vec3L(0, 0, 0), 0f)
    every { of(neq(INCUBATOR)) } returns null
  }
  private val async = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val eggRow = BestiaEgg(
    itemInstanceId = EGG_INSTANCE,
    speciesId = 1L,
    sex = Sex.FEMALE,
    individualValues = IndividualValues(),
    encodedAttackLearnLevels = "",
    hatchMillis = HATCH_MILLIS,
  )
  private val eggs = mockk<BestiaEggRepository> {
    every { findById(EGG_INSTANCE) } returns Optional.of(eggRow)
    every { save(any()) } answers { firstArg() }
  }
  private val rows = mockk<IncubatorRowRepository>(relaxed = true) {
    every { save(any()) } answers { firstArg() }
  }
  private val incubators = Incubators(rows, eggs, async)
  private val itemTemplates = mockk<ItemTemplateRegistry> {
    every { idOf(BreedingFarmService.BESTIA_EGG) } returns EGG
    every { weightOf(EGG) } returns 200
  }
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val skills = mockk<BreederSkills> {
    // Lv. 4: every real second warms the egg by 1.25 seconds.
    every { bloodlineStudyLevel(any()) } returns 4
  }
  private val hatching = mockk<Hatching>(relaxed = true)
  private val ownedBestias = mockk<OwnedBestiasPublisher>(relaxed = true)

  private lateinit var world: EcsWorld
  private val worldView = mockk<WorldView> {
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(world) }
    every { modify<Any?>(any(), any()) } answers { secondArg<World.(EntityId) -> Any?>().invoke(world, firstArg()) }
  }

  private val service = IncubatorService(
    structures, incubators, skills, itemTemplates, inventoryService, eggs, async, worldView, mockk(relaxed = true), clock,
  )
  private val system = IncubatorSystem(
    structures, incubators, hatching, mockk(relaxed = true), ownedBestias, async, worldView, clock,
  )

  init {
    world = testWorld(systems = listOf(system))
  }

  private val owner = world.createEntity { id ->
    add(id, Master(masterId = OWNER, name = "Breeder"))
    add(id, Position.fromVec3(Vec3L(1, 0, 0)))
    add(id, Inventory(mutableListOf(EggItems.inventoryItem(EGG, 200, EGG_INSTANCE))))
    add(id, CarryCapacity(current = 0, max = 10_000))
    add(id, KnownSkills(mutableMapOf()))
  }

  private fun place() = service.place(world, owner, INCUBATOR, EggSummary.of(eggRow))

  private fun tick(seconds: Long) {
    clock.advance(Duration.ofSeconds(seconds))
    world.tick(seconds.toFloat())
  }

  private fun holdsEgg(): Boolean {
    return world.get(owner, Inventory::class)!!.getItems().any { it.uniqueId == EGG_INSTANCE }
  }

  @Test
  fun `a placed egg leaves the bag and warms faster with Bloodline Study`() {
    assertNull(place())

    assertFalse(holdsEgg())
    verify { inventoryService.removeOneFromMaster(OWNER, EGG, 1, EGG_INSTANCE) }

    clock.advance(Duration.ofSeconds(100))
    assertEquals(125_000, incubators.of(INCUBATOR).hatchedMillisAt(clock.instant()))
  }

  @Test
  fun `an egg taken out keeps its progress`() {
    place()
    tick(100)

    assertNull(service.takeEgg(world, owner, INCUBATOR))

    assertTrue(holdsEgg())
    assertNull(incubators.of(INCUBATOR).egg)
    assertEquals(125_000, eggRow.hatchedMillis)
    verify { inventoryService.grantToMaster(OWNER, EGG, 1, uniqueId = EGG_INSTANCE) }
  }

  @Test
  fun `a ready egg hatches beside the incubator and empties it`() {
    place()

    tick(HATCH_MILLIS / 1000)

    verify { hatching.hatch(OWNER, EGG_INSTANCE, Vec3L(3, 0, 0)) }
    verify { ownedBestias.publish(ACCOUNT) }
    assertNull(incubators.of(INCUBATOR).egg)
  }

  @Test
  fun `without a free slot the egg waits and hatching is tried again later`() {
    every { hatching.hatch(any(), any(), any()) } throws OwnedBestiaPolicyViolationException("full")
    place()

    tick(HATCH_MILLIS / 1000)

    val waiting = incubators.of(INCUBATOR)
    assertTrue(waiting.waitingForSlot)
    assertEquals(EGG_INSTANCE, waiting.egg?.instanceId)

    every { hatching.hatch(any(), any(), any()) } returns Unit
    tick(30)
    verify(exactly = 1) { hatching.hatch(any(), any(), any()) }

    tick(31)
    verify(exactly = 2) { hatching.hatch(any(), any(), any()) }
    assertNull(incubators.of(INCUBATOR).egg)
  }

  private companion object {
    const val INCUBATOR = 6L
    const val OWNER = 42L
    const val ACCOUNT = 4L
    const val EGG = 441L
    const val EGG_INSTANCE = 9_000L
    const val HATCH_MILLIS = 1_000_000L
  }
}
