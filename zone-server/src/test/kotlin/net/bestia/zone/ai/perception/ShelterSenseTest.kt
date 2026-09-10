package net.bestia.zone.ai.perception

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.ecs.AiPipelineFixture
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What frightens a townsperson, and which door they pick.
 *
 * The first case is the one the whole sense is shaped around. Perception counts a player as hostile, so
 * a town wired to "an enemy is in sight" would empty every time somebody walked in - what is looked for
 * is a *blow landing*, and a stranger standing in the square is not one.
 */
class ShelterSenseTest {

  private val ai = AiPipelineFixture()

  private var doors = emptyList<Vec3L>()
  private val sut = ShelterSense(ai.aoi) { at, reach -> doors.filter { it.distance(at) <= reach } }

  private val square = Vec3L(100, 100, 0)

  @Test
  fun `a stranger walking through the square is not a fight`() {
    doors = listOf(Vec3L(110, 100, 0))
    ai.spawnPlayer(Vec3L(104, 100, 0))

    val villager = sense(spawnVillager())

    assertNull(recall(villager, TownsfolkDomain.THREAT_POSITION), "a passer-by emptied the town")
  }

  @Test
  fun `somebody taking a blow is`() {
    doors = listOf(Vec3L(110, 100, 0))
    val brawl = Vec3L(104, 100, 0)
    brawlAt(brawl)

    val villager = sense(spawnVillager())

    assertEquals(brawl, recall(villager, TownsfolkDomain.THREAT_POSITION))
    assertEquals(doors.single(), recall(villager, TownsfolkDomain.SHELTER_DOOR))
  }

  @Test
  fun `a fight far enough off is somebody else's business`() {
    doors = listOf(Vec3L(110, 100, 0))
    brawlAt(Vec3L(100 + ShelterSense.ALARM_RADIUS + 5, 100, 0))

    val villager = sense(spawnVillager())

    assertNull(recall(villager, TownsfolkDomain.THREAT_POSITION))
  }

  @Test
  fun `a door on the far side beats a nearer one behind the fight`() {
    val past = Vec3L(96, 100, 0)
    val away = Vec3L(108, 100, 0)
    doors = listOf(past, away)
    brawlAt(Vec3L(98, 100, 0))

    val villager = sense(spawnVillager())

    assertEquals(away, recall(villager, TownsfolkDomain.SHELTER_DOOR), "they ran straight through the brawl")
  }

  @Test
  fun `with the fight between them and every door, the nearest is still better than the street`() {
    val only = Vec3L(90, 100, 0)
    doors = listOf(only)
    brawlAt(Vec3L(96, 100, 0))

    val villager = sense(spawnVillager())

    assertEquals(only, recall(villager, TownsfolkDomain.SHELTER_DOOR))
  }

  @Test
  fun `the door does not change under them as the fight moves`() {
    val near = Vec3L(96, 100, 0)
    val far = Vec3L(108, 100, 0)
    doors = listOf(near, far)
    val brawler = brawlAt(Vec3L(108, 101, 0))

    val villager = sense(spawnVillager())
    assertEquals(near, recall(villager, TownsfolkDomain.SHELTER_DOOR))

    ai.teleport(brawler, Vec3L(96, 101, 0))
    sense(villager)

    assertEquals(near, recall(villager, TownsfolkDomain.SHELTER_DOOR), "they turned round mid-street")
  }

  @Test
  fun `once the fight has moved off they are released`() {
    doors = listOf(Vec3L(110, 100, 0))
    val brawler = brawlAt(Vec3L(104, 100, 0))

    val villager = sense(spawnVillager())
    ai.teleport(brawler, Vec3L(1_000, 1_000, 0))
    sense(villager)

    assertNull(recall(villager, TownsfolkDomain.THREAT_POSITION))
    assertNull(recall(villager, TownsfolkDomain.SHELTER_DOOR), "the door has to go too, or it is never re-picked")
  }

  @Test
  fun `a creature in the same street has its own answer to a fight`() {
    doors = listOf(Vec3L(110, 100, 0))
    brawlAt(Vec3L(104, 100, 0))

    val mob = sense(ai.spawnMob("aggressive_melee", square))

    assertNull(recall(mob, TownsfolkDomain.THREAT_POSITION))
  }

  /** A player being hit, which is the ordinary way a fight looks in a town. */
  private fun brawlAt(position: Vec3L): EntityId {
    val victim = ai.spawnPlayer(position)
    ai.recordHit(victim, attacker = OFFSTAGE_ATTACKER)
    return victim
  }

  private fun spawnVillager(): EntityId {
    return ai.spawnTownsfolk(LABOURER, home = square)
  }

  private fun sense(id: EntityId): EntityId {
    sut.sense(
      SenseContext(
        world = ai.world,
        entityId = id,
        agent = ai.agentOf(id),
        position = ai.positionOf(id),
        worldMemory = ai.sharedMemory.worldBoard(),
      )
    )
    return id
  }

  private fun <T> recall(id: EntityId, key: StateKey<T>): T? {
    return ai.agentOf(id).memory.get(key)
  }

  private companion object {
    val LABOURER = Occupation("labourer", "labourer", null, HourWindow(7, 17), HourWindow(22, 6))

    /** Who is swinging does not matter here - only that somebody is. */
    const val OFFSTAGE_ATTACKER = 999L
  }
}
