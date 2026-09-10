package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.settlement.SettlementSiteIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a town has people standing in it and when it does not.
 *
 * The case that decides the whole design is `a farmer is built for the player standing in his field`:
 * activation is tested at the **anchor** - where the clock says somebody would be - rather than at their
 * front door. Testing the door instead is the obvious implementation and is wrong in both directions at
 * once, and no assertion about a hamlet would catch it, because in a hamlet the two are the same place.
 */
class TownsfolkResidencySystemTest {

  private val world = testWorld()
  private val roster = mockk<TownsfolkRoster>()
  private val sites = mockk<SettlementSiteIndex>()
  private val indoors = IndoorRegistry()
  private val spawner = mockk<TownsfolkEntitySpawner>()

  private var hour = 12
  private val clock = mockk<BestiaClock>().also {
    every { it.now() } answers { BestiaDateTime(year = 1, month = 1, day = 1, hour = hour, minute = 0, second = 0) }
  }

  private val config = TownsfolkResidencyConfig(activationRadiusTiles = 100, spawnsPerPass = 4)

  private val farmer = TownsfolkRoster.Resident(
    identity = TownsfolkIdentity.of(SETTLEMENT, household = 0, member = 0),
    occupation = Occupation("farmer", "farmer", null, HourWindow(6, 19), HourWindow(21, 5)),
    home = HOME,
    homeBuilding = 1L,
    workplace = FIELD,
  )

  private val child = TownsfolkRoster.Resident(
    identity = TownsfolkIdentity.of(SETTLEMENT, household = 0, member = 1),
    occupation = Occupation("child", "child", null, null, HourWindow(21, 7)),
    home = HOME,
    homeBuilding = 1L,
    workplace = null,
  )

  private val sut = TownsfolkResidencySystem(roster, sites, indoors, spawner, clock, config)

  /** Who the system asked for, in order, and the bare entity each request produced. */
  private val built = LinkedHashMap<Long, EntityId>()

  init {
    every { sites.coveringWithin(any(), any(), any()) } returns listOf(SETTLEMENT)
    every { roster.of(SETTLEMENT) } returns listOf(farmer, child)
    every { spawner.emerge(any(), any(), any()) } answers {
      val identity = secondArg<Long>()
      world.createEntity { }.also { built[identity] = it }
    }
  }

  @Test
  fun `an empty world builds nobody`() {
    sut.update(world, DT)

    assertEquals(emptySet(), built.keys)
  }

  @Test
  fun `a player at the house gets the people who would be at the house`() {
    hour = 22
    player(HOME)

    sut.update(world, DT)

    // Both of them: the field is shut at ten at night, so the farmer's anchor is his own door.
    assertEquals(setOf(farmer.identity, child.identity), built.keys)
  }

  @Test
  fun `a farmer is built for the player standing in his field`() {
    // Nobody is anywhere near the village, and the farmer still has to be there - he is not at home at
    // noon, and a ring drawn round his house would leave the field empty for the player standing in it.
    hour = 12
    player(FIELD)

    sut.update(world, DT)

    assertEquals(setOf(farmer.identity), built.keys, "the child is at home, half a kilometre away")
  }

  @Test
  fun `and is not built twice when the player walks from field to house`() {
    hour = 12
    val walker = player(FIELD)
    sut.update(world, DT)

    move(walker, HOME)
    sut.update(world, DT)

    assertEquals(2, built.size, "the child was added; the farmer must not have been built a second time")
    assertEquals(setOf(farmer.identity, child.identity), built.keys)
  }

  @Test
  fun `somebody behind a door is left behind it`() {
    hour = 22
    indoors.enter(child.identity, HOME, HourWindow(21, 7))
    player(HOME)

    sut.update(world, DT)

    assertEquals(setOf(farmer.identity), built.keys, "IndoorEmergenceSystem decides when a door opens")
  }

  @Test
  fun `a pass builds no more than its budget`() {
    val crowd = (0 until 10).map {
      TownsfolkRoster.Resident(
        identity = TownsfolkIdentity.of(SETTLEMENT, household = it, member = 0),
        occupation = child.occupation,
        home = HOME,
        homeBuilding = 1L,
        workplace = null,
      )
    }
    every { roster.of(SETTLEMENT) } returns crowd
    player(HOME)

    sut.update(world, DT)

    assertEquals(config.spawnsPerPass, built.size, "a single pass filled the whole street at once")
  }

  @Test
  fun `walking away empties the street, but not at once`() {
    hour = 22
    val walker = player(HOME)
    sut.update(world, DT)
    val standing = world.entityCount

    move(walker, FAR_AWAY)
    sut.update(world, DT)
    assertEquals(standing, world.entityCount, "they vanished the instant the player turned round")

    tick(config.unloadDelaySeconds + 1f)

    assertTrue(world.entityCount < standing, "the street never emptied")
  }

  @Test
  fun `going through a door is not a death, and is not undone`() {
    // An entity that disappears has gone inside. Treating it as something to replace would put the person
    // straight back on the doorstep they just left, every quarter second, for the rest of the night.
    hour = 22
    player(HOME)
    sut.update(world, DT)
    val requests = built.size

    val gone = built.getValue(child.identity)
    indoors.enter(child.identity, HOME, HourWindow(21, 7))
    world.destroy(gone)
    world.tick(DT)

    sut.update(world, DT)

    assertEquals(requests, built.size, "somebody was rebuilt after walking through their own front door")
    assertFalse(world.hasEntity(gone))
  }

  private fun player(at: Vec3L): EntityId = world.createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, ActivePlayer)
  }

  private fun move(id: EntityId, to: Vec3L) {
    val position = world.getOrThrow(id, Position::class)
    position.x = to.x
    position.y = to.y
    position.z = to.z
  }

  private fun tick(seconds: Float) {
    var left = seconds
    while (left > 0f) {
      sut.update(world, DT)
      left -= DT
    }
  }

  private companion object {
    const val SETTLEMENT = 5
    const val DT = 0.25f

    val HOME = Vec3L(1_000, 1_000, 0)

    /** Well outside the ring from [HOME], which is the point: a field is not next to the house. */
    val FIELD = Vec3L(1_800, 1_000, 0)

    val FAR_AWAY = Vec3L(50_000, 50_000, 0)
  }
}
