package net.bestia.zone.questgen

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef

/** A plan made by hand: hear of the loss from [giver], then dig. */
fun lostRelicPlan(artifact: Int, giver: WorldTarget, score: Double = 1.0): QuestPlan {
  val args = mapOf("artifact" to TextArg.Name("Ashbrand"))

  return QuestPlan(
    seed = QuestSeed(QuestSeed.Kind.LOST_RELIC, Actor(ActorType.ARTIFACT, artifact), uniqueness = QuestSeed.Uniqueness.UNIQUE),
    motif = "lost_relic",
    title = TextRef("QUESTGEN_LOST_RELIC_TITLE", args),
    startsAt = giver,
    suggestedLevel = 80,
    steps = listOf(
      QuestPlan.Step(
        "hear_of_loss",
        QuestObjective(ObjectiveKind.TALK, ObjectiveScope.PERSONAL, 1, target = giver, text = TextRef("QUESTGEN_STEP_HEAR_OF_LOSS", args)),
        teaches = listOf(FactRef.event(30)),
      ),
      QuestPlan.Step(
        "dig_relic",
        QuestObjective(
          ObjectiveKind.EXAMINE, ObjectiveScope.PERSONAL, 1, stage = 1, target = WorldTarget.cache(55),
          text = TextRef("QUESTGEN_STEP_DIG_RELIC", args),
        ),
      ),
    ),
    score = score,
  )
}
