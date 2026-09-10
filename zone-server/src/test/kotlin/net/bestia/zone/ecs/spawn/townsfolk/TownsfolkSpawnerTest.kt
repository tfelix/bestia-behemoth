package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.worldgen.pop.Sector
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a spawned townsperson is, as against a spawned creature.
 *
 * Three markers and the absence of a fourth, and the absence is the load-bearing one. A town's population
 * is regenerated from its seed whenever somebody comes near, so a row per villager would be a stored copy
 * of something already derivable - and one that goes stale the day the expansion changes. `Persistent` is
 * what the periodic sweep queries on, so not carrying it is the whole of not being persisted.
 */
class TownsfolkSpawnerTest {

  private val world = testWorld()
  private val placement = mockk<HouseholdPlacement>()
  private val catalogue = mockk<BestiaCatalogue>()
  private val bestiaSpawner = mockk<BestiaEntitySpawner>()
  private val occupations = OccupationCatalogue().apply { load() }

  private val persistent = slot<Boolean>()

  /** Every blackboard handed to the mob spawner, in the order the household was walked. */
  private val seeded = mutableListOf<Blackboard>()

  private val household = Household(
    index = 3,
    business = -1,
    sector = Sector.FARM,
    wealth = 0.3,
    members = listOf(Member(40, Kinship.HEAD), Member(38, Kinship.SPOUSE), Member(9, Kinship.CHILD)),
  )

  private val sut: TownsfolkEntitySpawner

  init {
    val bestia = mockk<Bestia>()
    every { bestia.id } returns 3L
    every { catalogue.byIdentifier(any()) } returns bestia

    every { placement.of(any(), any()) } returns HouseholdPlacement.Placement(
      settlement = 12,
      household = household,
      home = Vec3L(100, 100, 0),
      homeBuilding = 7_777L,
      workplace = Vec3L(140, 100, 0),
    )
    every { placement.occupationFor(any(), any()) } answers {
      val member = secondArg<Member>()
      occupations.getOrThrow(if (member.kinship == Kinship.CHILD) "child" else "farmer")
    }

    every {
      bestiaSpawner.spawnMob(any(), any(), any(), any(), any(), capture(persistent), capture(seeded), any())
    } answers { world.createEntity { } }

    sut = TownsfolkEntitySpawner(placement, catalogue, bestiaSpawner)
  }

  @Test
  fun `everybody in the household is put down`() {
    assertEquals(household.members.size, sut.spawnHousehold(world, settlement = 12, household = 3).size)
  }

  @Test
  fun `a townsperson is not persisted`() {
    val spawned = sut.spawnHousehold(world, settlement = 12, household = 3)

    assertFalse(persistent.captured, "the spawner asked for a database row")
    for (id in spawned) {
      assertFalse(world.has(id, Persistent::class), "entity $id would be caught by the persistence sweep")
    }
  }

  @Test
  fun `a townsperson cannot be hurt, and may be thought about less often`() {
    val spawned = sut.spawnHousehold(world, settlement = 12, household = 3)

    for (id in spawned) {
      assertTrue(world.has(id, Invulnerable::class), "entity $id is killable")
      assertTrue(world.has(id, AiThrottleable::class), "entity $id would think at full cadence forever")
    }
  }

  @Test
  fun `each of them knows which person they are`() {
    val spawned = sut.spawnHousehold(world, settlement = 12, household = 3)
    val identities = spawned.map { world.get(it, Townsfolk::class)?.identity }

    assertEquals(spawned.size, identities.filterNotNull().toSet().size, "two of them are the same person")
    for ((member, identity) in identities.filterNotNull().withIndex()) {
      assertEquals(12, TownsfolkIdentity.settlementOf(identity))
      assertEquals(3, TownsfolkIdentity.householdOf(identity))
      assertEquals(member, TownsfolkIdentity.memberOf(identity))
    }
  }

  @Test
  fun `the child is a child and the adults farm`() {
    // Asserted on what is handed to the mob spawner, because that is the only route an occupation has: the
    // archetype is the same for all three of them, and the agent is built from this blackboard.
    sut.spawnHousehold(world, settlement = 12, household = 3)

    assertEquals(
      listOf("farmer", "farmer", "child"),
      seeded.map { it.get(TownsfolkDomain.OCCUPATION)?.id }
    )
  }

  @Test
  fun `the whole household is sent to the same workplace`() {
    sut.spawnHousehold(world, settlement = 12, household = 3)

    assertEquals(
      List(household.members.size) { Vec3L(140, 100, 0) },
      seeded.map { it.get(TownsfolkDomain.WORK_POSITION) }
    )
  }

  @Test
  fun `nothing is spawned where nobody lives`() {
    every { placement.of(any(), any()) } returns null

    assertEquals(emptyList(), sut.spawnHousehold(world, settlement = 12, household = 3))
  }

}
