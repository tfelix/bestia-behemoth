package net.bestia.zone.questgen.text

import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.zone.questgen.persistence.StoredQuestPlan.State
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.plan.QuestCatalogue
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import net.bestia.zone.text.TextSource
import net.bestia.zone.text.TextSubject
import net.bestia.zone.townsfolk.knowledge.ChronicleNames
import net.bestia.zone.world.WorldService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * The texts of the quest plans: what the giver says, and the journal line of each step. Every one carries a
 * lead: the giver sends the player to the next step, and a journal line names where to go.
 */
@Component
class QuestTextSource(
  private val plans: StoredQuestPlanRepository,
  private val catalogue: QuestCatalogue,
  private val worldService: WorldService,
) : TextSource {

  override fun wanted(): List<TextSource.Wanted> {
    return plans.findAllByStateIn(listOf(State.PLANNED, State.OFFERED, State.RUNNING)).flatMap { stored ->
      val steps = stored.plan.steps.indices.filter { briefOfStep(stored.plan, it) != null }

      listOf(TextSource.Wanted(TextSubject.questOffer(stored.id), PRIORITY)) +
        steps.map { TextSource.Wanted(TextSubject.questStep(stored.id, it), PRIORITY) }
    }
  }

  override fun briefOf(subject: String): TextBrief? {
    val (planId, step) = TextSubject.questPartsOf(subject) ?: return null
    val plan = plans.findByIdOrNull(planId)?.plan ?: return null

    return if (step == null) briefOfOffer(plan) else briefOfStep(plan, step)
  }

  private fun briefOfOffer(plan: QuestPlan): TextBrief? {
    val motif = catalogue.motif(plan.motif) ?: return null
    val names = namesOf(plan)
    val about = filled(motif.offer, names) ?: return null
    // The giver's own step is done by listening, so the lead is the step after it.
    val next = plan.steps.getOrNull(1)?.let { catalogue.step(it.pattern) }?.let { filled(it.brief, names) } ?: return null
    val town = names["town"] ?: return null

    return TextBrief(
      task = "A townsperson of $town speaks to a traveller who asked about an old matter. Write what they say: " +
        "3 to 5 sentences, 50 to 90 words, plain spoken words, no greeting. Tell ${about.text}, as the FACT SHEET " +
        "says. End by telling the traveller to ${next.text}.",
      allowedNames = allowedNamesOf(plan, names),
      facts = factsOf(plan),
      mustName = (about.used + next.used).distinct(),
    )
  }

  private fun briefOfStep(plan: QuestPlan, step: Int): TextBrief? {
    val pattern = plan.steps.getOrNull(step)?.let { catalogue.step(it.pattern) } ?: return null
    val names = namesOf(plan)
    val deed = filled(pattern.brief, names) ?: return null

    return TextBrief(
      task = "Write one entry of a traveller's journal, as a note to oneself. One or two sentences, 15 to 35 words, " +
        "starting with \"I\". It must say that the writer has to ${deed.text}. Add one detail of the past from " +
        "the FACT SHEET.",
      allowedNames = allowedNamesOf(plan, names),
      facts = factsOf(plan),
      mustName = deed.used,
    )
  }

  private class Filled(val text: String, val used: List<String>)

  /** [template] with its `{slots}` filled, and the names it took. Null when the plan has no name for a slot. */
  private fun filled(template: String, names: Map<String, String>): Filled? {
    val slots = SLOT.findAll(template).map { it.groupValues[1] }.toList()
    if (slots.any { it !in names }) {
      return null
    }

    return Filled(SLOT.replace(template) { names.getValue(it.groupValues[1]) }, slots.map(names::getValue).distinct())
  }

  /** The proper nouns of the plan by their slot. A place nobody names any more is no name. */
  private fun namesOf(plan: QuestPlan): Map<String, String> {
    return plan.title.args.mapNotNull { (slot, arg) -> (arg as? TextArg.Name)?.let { slot to it.value } }.toMap()
  }

  private fun allowedNamesOf(plan: QuestPlan, names: Map<String, String>): List<AllowedName> {
    val chronicle = chronicle()
    val artifact = chronicle.artifacts[plan.seed.subject.index]
    val forger = ChronicleNames.figureOf(chronicle, artifact.forgedBy)

    val described = names.map { (slot, name) ->
      val what = when (slot) {
        "artifact" -> "a ${artifact.kind.name.lowercase(Locale.ROOT)} of ${artifact.material}, made in ${artifact.forgedYear}"
        "keeper" -> "one who once held it, long dead"
        else -> "a town"
      }
      AllowedName(name, what)
    }

    // One town can fill two slots, and the writer is told of it once.
    return (described + listOfNotNull(forger?.let { AllowedName(it, "the one who made it") })).distinctBy { it.name }
  }

  /** The artifact's own story in the chronicle, one line per event. */
  private fun factsOf(plan: QuestPlan): List<String> {
    val chronicle = chronicle()
    val artifact = plan.seed.subject.takeIf { it.type == ActorType.ARTIFACT } ?: return emptyList()
    val name = ChronicleNames.artifactOf(chronicle, artifact.index)

    return listOf("Subject: $name") + chronicle.provenanceOf(artifact.index).map { "${it.year}: ${it.detail}" }
  }

  private fun chronicle(): Chronicle {
    return worldService.generated.world.chronicle
  }

  private companion object {
    /** Quest texts are the first a player meets, so they are written before the rest of the history. */
    const val PRIORITY = 10
    val SLOT = Regex("""\{(\w+)}""")
  }
}
