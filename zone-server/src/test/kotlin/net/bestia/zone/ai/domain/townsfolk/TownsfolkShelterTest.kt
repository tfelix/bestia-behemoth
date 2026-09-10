package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.core.agent.SimpleAgent
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * What a townsperson decides with a fight going on nearby.
 *
 * `ShelterSenseTest` settles which door is picked; this settles what is done about it. The two cases that
 * carry the design are the guard, who is the whole reason sheltering is a property of the trade rather
 * than of everybody, and the villager with nowhere to run - who must go on with their day rather than
 * freeze, because the think stage plans for the top goal only.
 */
class TownsfolkShelterTest {

  private val planner = Planner()
  private val home = Vec3L(100, 100, 0)
  private val post = Vec3L(140, 100, 0)
  private val door = Vec3L(112, 100, 0)
  private val brawl = Vec3L(104, 100, 0)

  private val guard = Occupation("guard", "guard", "barracks", HourWindow(6, 18), HourWindow(22, 6), true)
  private val labourer = Occupation("labourer", "labourer", null, HourWindow(7, 17), HourWindow(22, 6))

  @Test
  fun `a fight in the street outranks the day's work`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = home, occupation = labourer, threat = brawl, shelter = door),
      Blackboard()
    )

    assertEquals("TakeShelter", plan?.goal?.name)
    assertEquals(listOf("goToShelter", "shelterAtDoor"), plan?.actions?.map { it.name })
  }

  @Test
  fun `already in the doorway, the thing to do is stay in it`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = door, occupation = labourer, threat = brawl, shelter = door),
      Blackboard()
    )

    assertEquals(listOf("shelterAtDoor"), plan?.actions?.map { it.name })
  }

  @Test
  fun `the guard stands his ground`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = post, occupation = guard, threat = brawl, shelter = door),
      Blackboard()
    )

    assertEquals("WorkShift", plan?.goal?.name, "the guard left his post because somebody was fighting on it")
  }

  @Test
  fun `with no door in reach, the day goes on rather than stopping`() {
    // A farmer alone in a distant field. AiThinkSystem plans for the top goal only, so a goal available
    // with nothing to satisfy it leaves the agent with no plan at all - the same trap WorkShift avoids.
    val plan = planner.makePlanForAgent(
      townsperson(at = home, occupation = labourer, threat = brawl, shelter = null),
      Blackboard()
    )

    assertNotNull(plan)
    assertNotEquals("TakeShelter", plan.goal.name)
  }

  @Test
  fun `with the street quiet again there is nothing to shelter from`() {
    val plan = planner.makePlanForAgent(
      townsperson(at = door, occupation = labourer, threat = null, shelter = null),
      Blackboard()
    )

    assertNotEquals("TakeShelter", plan?.goal?.name)
  }

  private fun townsperson(
    at: Vec3L,
    occupation: Occupation,
    threat: Vec3L?,
    shelter: Vec3L?,
  ): SimpleAgent {
    val memory = Blackboard().apply {
      set(TownsfolkDomain.POSITION, at, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOME_POSITION, home, Blackboard.PERMANENT)
      set(TownsfolkDomain.WORK_POSITION, post, Blackboard.PERMANENT)
      set(TownsfolkDomain.HOUR_OF_DAY, WORKING_HOUR, Blackboard.PERMANENT)
      set(TownsfolkDomain.DAY_INDEX, TODAY, Blackboard.PERMANENT)
      set(TownsfolkDomain.WANDER_RADIUS, TownsfolkDomain.DEFAULT_LOITER_RADIUS, Blackboard.PERMANENT)
      set(TownsfolkDomain.TIREDNESS, 30, Blackboard.PERMANENT)
      set(TownsfolkDomain.RESTLESSNESS, 100, Blackboard.PERMANENT)
      set(TownsfolkDomain.OCCUPATION, occupation, Blackboard.PERMANENT)
      threat?.let { set(TownsfolkDomain.THREAT_POSITION, it, Blackboard.PERMANENT) }
      shelter?.let { set(TownsfolkDomain.SHELTER_DOOR, it, Blackboard.PERMANENT) }
    }

    return SimpleAgent(
      name = "commoner",
      goals = TownsfolkDomain.Goals.ALL,
      memory = memory,
      actionResolver = TownsfolkDomainFixture.resolver(),
    )
  }

  private companion object {
    /** Inside every shift here, so shelter has something to outrank. */
    const val WORKING_HOUR = 9
    const val TODAY = 40L
  }
}
