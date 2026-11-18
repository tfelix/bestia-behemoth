package net.bestia.zone.questgen

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class StoredQuestPlanTest {

  @Autowired
  private lateinit var plans: StoredQuestPlanRepository

  @AfterEach
  fun forget() {
    plans.deleteAll()
  }

  @Test
  fun `a plan is read back as it was stored, and found by its state`() {
    val plan = QuestPlan(
      seed = QuestSeed(QuestSeed.Kind.LOST_RELIC, Actor(ActorType.ARTIFACT, 3), listOf(Actor(ActorType.SITE, 8)), QuestSeed.Uniqueness.UNIQUE),
      motif = "lost_relic",
      title = TextRef("QUESTGEN_LOST_RELIC_TITLE", mapOf("artifact" to TextArg.Name("Ashbrand"))),
      startsAt = WorldTarget.townsfolk(4_000_012),
      suggestedLevel = 20,
      steps = listOf(
        QuestPlan.Step(
          pattern = "read_epitaph",
          objective = QuestObjective(
            ObjectiveKind.EXAMINE, ObjectiveScope.PERSONAL, 1, target = WorldTarget.grave(7), text = TextRef("QUESTGEN_STEP_READ_EPITAPH"),
          ),
          teaches = listOf(FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.NAME)),
        ),
      ),
      score = 0.8,
    )

    val stored = plans.save(StoredQuestPlan(plan))

    assertEquals(plan, plans.findAllByState(StoredQuestPlan.State.PLANNED).single { it.id == stored.id }.plan)
    assertEquals(3, stored.subjectIndex)
  }
}
