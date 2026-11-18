package net.bestia.zone.questgen

import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.seed.ThreadSeeds
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Planning on the generated test world, which the boot already planned once. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class QuestPlanningTest {

  @Autowired
  private lateinit var planning: QuestPlanning

  @Autowired
  private lateinit var plans: StoredQuestPlanRepository

  @Autowired
  private lateinit var seeds: ThreadSeeds

  @Test
  fun `a seed is planned once, and a second run changes nothing`() {
    val before = plans.findAll().map { it.id to it.plan }

    val again = planning.planNewSeeds()

    assertTrue(again.planned.isEmpty(), "a restart planned ${again.planned.size} quests a second time")
    assertEquals(before, plans.findAll().map { it.id to it.plan })
  }

  @Test
  fun `every lost artifact has a plan or a reason why not`() {
    val planned = plans.findAll().map { it.plan.seed }.toSet()
    val unplannable = planning.planNewSeeds().unplannable

    assertEquals(seeds.seeds().toSet(), planned + unplannable.keys)
    assertTrue(unplannable.values.all { it.isNotEmpty() }, "a seed without a plan must say what the world is missing")
    println("QUEST CENSUS planned=${planned.size} unplannable=${unplannable.mapKeys { it.key.subject }}")
    plans.findAll().forEach { println("QUEST PLAN ${it.plan.seed.subject} ${it.plan.steps.map { s -> s.pattern }} score=${it.plan.score}") }
  }
}
