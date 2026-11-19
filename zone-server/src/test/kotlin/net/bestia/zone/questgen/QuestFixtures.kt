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

const val RELIC_ITEM_ID = 900L

/**
 * A plan made by hand: hear of the loss from [giver], then dig. With [home] the master then decides, and may
 * bring the artifact to that town.
 */
fun lostRelicPlan(artifact: Int, giver: WorldTarget, score: Double = 1.0, home: Int? = null): QuestPlan {
  val args = mapOf("artifact" to TextArg.Name("Ashbrand"), "town" to TextArg.Name("Dunmere"), "near" to TextArg.Name("Hale")) +
    (home?.let { mapOf("home" to TextArg.Name("Hale")) } ?: emptyMap())

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
    ) + returnStepsTo(home, args),
    score = score,
  )
}

private fun returnStepsTo(home: Int?, args: Map<String, TextArg>): List<QuestPlan.Step> {
  if (home == null) {
    return emptyList()
  }

  val choices = listOf(
    QuestObjective.Choice("keep", TextRef("QUESTGEN_CHOICE_KEEP_RELIC", args)),
    QuestObjective.Choice("return", TextRef("QUESTGEN_CHOICE_RETURN_RELIC", args)),
  )

  return listOf(
    QuestPlan.Step(
      "decide",
      QuestObjective(
        ObjectiveKind.DECIDE, ObjectiveScope.PERSONAL, 1, stage = 2, text = TextRef("QUESTGEN_STEP_DECIDE_RELIC", args), choices = choices,
      ),
    ),
    QuestPlan.Step(
      "return_relic",
      QuestObjective(
        ObjectiveKind.BRING, ObjectiveScope.PERSONAL, 1, itemId = RELIC_ITEM_ID, stage = 3, target = WorldTarget.settlement(home),
        text = TextRef("QUESTGEN_STEP_RETURN_RELIC", args), afterChoice = "return",
      ),
    ),
  )
}
