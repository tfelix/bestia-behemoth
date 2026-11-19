package net.bestia.zone.questgen.plan

import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.questgen.plan.QuestCatalogue.Motif
import net.bestia.zone.questgen.plan.QuestCatalogue.StepPattern
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Turns one seed into the best plan this world allows, or into none.
 *
 * Payoff first: the planner starts at the goal of the motif and walks back through the steps that could lead
 * there. A chain is only a plan when every step points at something real, so a quest without a payoff is never
 * made. No randomness: the same world and the same catalogues give the same plans.
 */
class QuestPlanner(
  private val catalogue: QuestCatalogue,
  private val world: QuestWorld,
) {

  /** [missing] says why there is no plan: what this world lacks for the chain that came closest. */
  class Outcome(val plan: QuestPlan?, val missing: Set<BindSlot> = emptySet())

  /** @param taken what other plans already point at; a plan that shares none of it is preferred. */
  fun plan(seed: QuestSeed, taken: Set<WorldTarget> = emptySet()): Outcome {
    val motif = catalogue.motifFor(seed.kind) ?: return Outcome(null)
    val bound = Bound(seed, taken)

    val candidates = chainsTo(motif.goal, motif.steps, onPath = emptySet())
      .filter { it.size in MIN_STEPS..MAX_STEPS }
      .distinct()
      .take(MAX_CANDIDATES)
      .mapNotNull { chain -> bound.all(chain)?.let { Candidate(chain, it, endingOf(motif, bound)) } }
    val best = candidates.maxByOrNull { scoreOf(it, taken) } ?: return Outcome(null, bound.missing)

    return Outcome(planOf(seed, motif, best, scoreOf(best, taken)))
  }

  /** The best real thing per slot, asked of the world once. */
  private inner class Bound(private val seed: QuestSeed, private val taken: Set<WorldTarget>) {

    private val bySlot = HashMap<BindSlot, Binding?>()
    private var fewestLacking: Set<BindSlot>? = null

    val missing: Set<BindSlot>
      get() = fewestLacking.orEmpty()

    fun of(slot: BindSlot): Binding? {
      return bySlot.getOrPut(slot) {
        val found = world.bindings(slot, seed)
        found.firstOrNull { it.target !in taken } ?: found.firstOrNull()
      }
    }

    /** A binding for every step, or null when one step points at nothing. */
    fun all(chain: List<StepPattern>): List<Binding>? {
      val bindings = chain.map { of(it.binds) }
      val lacking = chain.filter { of(it.binds) == null }.mapTo(sortedSetOf()) { it.binds }
      if (lacking.isNotEmpty() && lacking.size < (fewestLacking?.size ?: Int.MAX_VALUE)) {
        fewestLacking = lacking
      }

      return bindings.filterNotNull().takeIf { lacking.isEmpty() }
    }
  }

  /** The decide point and what follows each choice. Null when the motif has none or this world cannot hold it. */
  private class Ending(val decide: QuestCatalogue.Decide, val after: Map<String, Pair<StepPattern, Binding>>)

  private class Candidate(val chain: List<StepPattern>, val bindings: List<Binding>, val ending: Ending?) {

    val tensions: List<Int>
      get() = chain.map { it.tension } + listOfNotNull(ending?.decide?.tension) +
        listOfNotNull(ending?.after?.values?.maxOfOrNull { it.first.tension })

    val allBindings: List<Binding>
      get() = bindings + ending?.after?.values.orEmpty().map { it.second }
  }

  private fun endingOf(motif: Motif, bound: Bound): Ending? {
    val decide = motif.decide ?: return null
    val after = decide.choices.mapNotNull { choice ->
      val then = choice.then ?: return@mapNotNull null
      // A choice that leads nowhere in this world would be a false promise, so then nothing is decided at all.
      val binding = bound.of(then.binds) ?: return null
      choice.id to (then to binding)
    }

    return Ending(decide, after.toMap())
  }

  /** Every chain of [steps] that ends in a step giving [goal], walked back from that step. */
  private fun chainsTo(goal: String, steps: List<StepPattern>, onPath: Set<String>): List<List<StepPattern>> {
    return steps
      .filter { goal in it.gives && it.id !in onPath }
      .flatMap { last -> chainsMeeting(last.needs.toList(), steps, onPath + last.id).map { (it + last).distinctBy(StepPattern::id) } }
  }

  /** Every way to meet all of [needs], each as one chain. */
  private fun chainsMeeting(needs: List<String>, steps: List<StepPattern>, onPath: Set<String>): List<List<StepPattern>> {
    if (needs.isEmpty()) {
      return listOf(emptyList())
    }

    val first = chainsTo(needs.first(), steps, onPath)
    val rest = chainsMeeting(needs.drop(1), steps, onPath)

    return first.flatMap { head -> rest.map { tail -> (head + tail).distinctBy(StepPattern::id) } }
  }

  private fun scoreOf(candidate: Candidate, taken: Set<WorldTarget>): Double {
    val factsTaught = candidate.allBindings.sumOf { it.teaches.size }
    val reused = candidate.allBindings.count { it.target in taken }

    return arcOf(candidate.tensions) *
      (1.0 + HISTORY_WEIGHT * factsTaught) *
      REUSE_PENALTY.pow(reused) *
      (1.0 / (1.0 + walkOf(candidate.bindings) / LONG_WALK_METRES))
  }

  /** 1 for a chain that rises to one peak and then falls, less for every pair of steps that breaks the arc. */
  private fun arcOf(tensions: List<Int>): Double {
    if (tensions.size < 2) {
      return 1.0
    }

    val peak = tensions.indexOf(tensions.max())
    val inArc = tensions.zipWithNext().withIndex().count { (index, pair) ->
      if (index < peak) pair.first < pair.second else pair.first > pair.second
    }

    return inArc.toDouble() / (tensions.size - 1)
  }

  private fun walkOf(bindings: List<Binding>): Double {
    return bindings.zipWithNext().sumOf { (from, to) -> hypot(to.at.x - from.at.x, to.at.y - from.at.y) }
  }

  private fun planOf(seed: QuestSeed, motif: Motif, best: Candidate, score: Double): QuestPlan {
    val args = world.argsOf(seed) + best.allBindings.flatMap { it.args.entries }.associate { it.key to it.value }
    val steps = best.chain.zip(best.bindings).mapIndexed { stage, (pattern, binding) -> stepOf(pattern, binding, stage, args) }

    return QuestPlan(
      seed = seed,
      motif = motif.id,
      title = TextRef(motif.title, args),
      startsAt = best.bindings.first().target,
      suggestedLevel = world.levelOf(seed),
      steps = steps + endingStepsOf(best.ending, firstStage = steps.size, args),
      score = score,
    )
  }

  private fun endingStepsOf(ending: Ending?, firstStage: Int, args: Map<String, TextArg>): List<QuestPlan.Step> {
    if (ending == null) {
      return emptyList()
    }

    val decide = QuestObjective(
      ObjectiveKind.DECIDE, ObjectiveScope.PERSONAL, required = 1, stage = firstStage,
      text = TextRef(ending.decide.text, args),
      choices = ending.decide.choices.map { QuestObjective.Choice(it.id, TextRef(it.text, args)) },
    )
    val after = ending.after.map { (choice, then) ->
      stepOf(then.first, then.second, firstStage + 1, args, afterChoice = choice)
    }

    return listOf(QuestPlan.Step(DECIDE_PATTERN, decide)) + after
  }

  private fun stepOf(
    pattern: StepPattern,
    binding: Binding,
    stage: Int,
    args: Map<String, TextArg>,
    afterChoice: String? = null,
  ): QuestPlan.Step {
    val objective = QuestObjective(
      pattern.kind, pattern.scope, required = binding.amount, itemId = binding.itemId, stage = stage,
      target = binding.target, text = pattern.text?.let { TextRef(it, args) }, afterChoice = afterChoice,
    )

    return QuestPlan.Step(pattern.id, objective, binding.teaches)
  }

  private companion object {
    /** A town's request for goods is one step. A chain through the history has more by its motif. */
    const val MIN_STEPS = 1
    const val MAX_STEPS = 5

    /** The catalogues are small, so this only guards against a motif that explodes. */
    const val MAX_CANDIDATES = 32

    /** Each fact a chain teaches makes it this much better. */
    const val HISTORY_WEIGHT = 0.2

    /** A chain that points at what another plan already uses is worth this much of its score, per shared thing. */
    const val REUSE_PENALTY = 0.6

    /** A walk of this length halves the score. */
    const val LONG_WALK_METRES = 20_000.0

    const val DECIDE_PATTERN = "decide"
  }
}
