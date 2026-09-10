package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A fight in the square, through the real pipeline: the sense, the planner, the behaviour and the walk.
 *
 * `TownsfolkShelterTest` settles what is decided and `ShelterSenseTest` which door; this settles that a
 * villager actually gets there and that the street fills again afterwards - the half that would quietly
 * not happen if the plan were adopted and never executed.
 */
class TownsfolkShelterScenarioTest {

  private lateinit var ai: AiPipelineFixture

  private val square = Vec3L(100, 100, 0)

  /** Behind the villager relative to the fight, and inside the alarm radius from both. */
  private val door = Vec3L(94, 100, 0)
  private val street = Vec3L(106, 100, 0)

  private val post = Vec3L(140, 100, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.doorsteps.add(door)
    ai.hourOfDay = WORKING_HOUR
  }

  @Test
  fun `a brawl in the street puts a villager in a doorway`() {
    val villager = ai.spawnTownsfolk(LABOURER, home = square)
    val brawler = ai.spawnPlayer(street)

    fightOn(brawler, ticks = 20 * 5)
    assertEquals("TakeShelter", ai.goalNameOf(villager))

    fightUntil(brawler, maxTicks = 20 * 60) {
      ai.positionOf(villager).distance(door) <= TownsfolkDomain.DOORSTEP_RADIUS
    }
  }

  @Test
  fun `the guard does not leave his post for it`() {
    // The one occupation that holds its ground, and the reason sheltering belongs to the trade rather
    // than to everybody. A town where the watch runs too has nobody left to be reassured by.
    val guard = ai.spawnTownsfolk(GUARD, home = post, post = post)
    val brawler = ai.spawnPlayer(Vec3L(post.x + 6, post.y, post.z))

    ai.tickUntilGoal(guard, "WorkShift")
    fightOn(brawler, ticks = 20 * 10)

    assertEquals("WorkShift", ai.goalNameOf(guard))
    assertTrue(
      ai.positionOf(guard).distance(post) <= TownsfolkDomain.DOORSTEP_RADIUS,
      "he abandoned his post ${ai.positionOf(guard).distance(post)} tiles for a scuffle beside it"
    )
  }

  @Test
  fun `once the fight moves on the street fills again`() {
    val villager = ai.spawnTownsfolk(LABOURER, home = square)
    val brawler = ai.spawnPlayer(street)

    fightOn(brawler, ticks = 20 * 5)
    assertEquals("TakeShelter", ai.goalNameOf(villager))

    ai.teleport(brawler, Vec3L(1_000, 1_000, 0))

    // Back to ordinary business rather than merely off the shelter goal: what would go unnoticed is a
    // villager released into no goal at all, which looks exactly like still cowering.
    ai.tickUntilGoal(villager, "Loiter")
  }

  /**
   * Keeps hitting [victim] while the world ticks.
   *
   * A brawl is repeated blows, and the sense only counts one inside `ShelterSense.ALARM_MEMORY_MS` - so a
   * single recorded hit at the top of a long scenario would go quiet partway through on a slow machine
   * and the test would fail for the clock rather than for the code.
   */
  private fun fightOn(victim: EntityId, ticks: Int) {
    repeat(ticks / BLOWS_EVERY) {
      ai.recordHit(victim, attacker = OFFSTAGE_ATTACKER)
      ai.tick(times = BLOWS_EVERY)
    }
  }

  private fun fightUntil(victim: EntityId, maxTicks: Int, condition: () -> Boolean) {
    ai.tickUntil(maxTicks, describe = { "never happened while the fight went on" }) {
      ai.recordHit(victim, attacker = OFFSTAGE_ATTACKER)
      condition()
    }
  }

  private companion object {
    const val WORKING_HOUR = 9
    const val BLOWS_EVERY = 10
    const val OFFSTAGE_ATTACKER = 999L

    val LABOURER = Occupation("labourer", "labourer", null, HourWindow(7, 17), HourWindow(22, 6))
    val GUARD = Occupation("guard", "guard", "barracks", HourWindow(6, 18), HourWindow(22, 6), true)
  }
}
