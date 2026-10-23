package net.bestia.zone.ai.ecs

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the creatures' minds are doing: how many are at each level of detail, how often they think and what comes
 * of it, what planning costs, and how often they change their minds. Planning is the expensive half of the AI
 * and a goal that flaps between two is the classic way for it to go wrong without anything failing.
 *
 * Recorded on the tick thread. The default registry serves tests that build the systems by hand.
 */
@Component
class AiMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** How one think of one agent ended. */
  enum class Think {
    /** Nothing worth doing; any plan is dropped. */
    NO_GOAL,

    /** The goal is the one it already has a plan for. */
    PLAN_KEPT,

    /** The tick's planning budget was spent; it thinks again next tick. */
    DEFERRED,

    /** A plan was found and taken. */
    ADOPTED,

    /** Every goal it tried had no plan; they are set aside for a while. */
    EXHAUSTED,
  }

  /** How one step of a plan ended, as the act stage saw it. */
  enum class Act {
    /** The step finished and the plan goes on. */
    STEP_DONE,

    /** The last step finished. */
    PLAN_FINISHED,

    /** A step failed and the plan was dropped to be made again. */
    FAILED,
  }

  private val thinks = Think.entries.associateWith { outcome ->
    Counter.builder("zone.ai.thinks")
      .tag("outcome", outcome.name.lowercase())
      .description("Times an agent thought, by how it ended")
      .register(registry)
  }

  private val acts = Act.entries.associateWith { result ->
    Counter.builder("zone.ai.steps")
      .tag("result", result.name.lowercase())
      .description("Plan steps the act stage ran to an end")
      .register(registry)
  }

  private val blocked = Counter.builder("zone.ai.goals.blocked")
    .description("Goals set aside because no plan was found for them")
    .register(registry)

  private val preemptions = Counter.builder("zone.ai.goals.preempted")
    .description("Plans dropped for another goal while they were still running; a high rate is an agent changing its mind")
    .register(registry)

  private val searches = listOf("found", "none").associateWith { result ->
    Counter.builder("zone.ai.plan.searches")
      .tag("result", result)
      .description("Planner searches by whether they found a plan")
      .register(registry)
  }

  private val iterations = DistributionSummary.builder("zone.ai.plan.iterations")
    .description("Search iterations one planner search took; the cap is 10 000 and a tick shares 5 000")
    .serviceLevelObjectives(10.0, 100.0, 1_000.0, 5_000.0)
    .register(registry)

  private val adoptedByGoal = ConcurrentHashMap<String, Counter>()
  private val failedByAction = ConcurrentHashMap<String, Counter>()

  private val agents = AiDetail.entries.associateWith { AtomicInteger() }

  init {
    for ((detail, count) in agents) {
      Gauge.builder("zone.ai.agents", count) { it.get().toDouble() }
        .tag("detail", detail.name.lowercase())
        .description("Creatures with an AI at each level of detail, as of the last full sweep (once a second)")
        .register(registry)
    }
  }

  fun think(outcome: Think) {
    thinks.getValue(outcome).increment()
  }

  fun act(result: Act) {
    acts.getValue(result).increment()
  }

  fun goalBlocked() {
    blocked.increment()
  }

  /** A plan for [goal] was adopted; [preempted] is true when it replaced one still running for another goal. */
  fun adopted(goal: String, preempted: Boolean) {
    adoptedByGoal.computeIfAbsent(goal) { name ->
      Counter.builder("zone.ai.goals.adopted")
        .tag("goal", name)
        .description("Plans adopted, by the goal they serve; goals are named in the AI profiles")
        .register(registry)
    }.increment()

    if (preempted) preemptions.increment()
  }

  fun actionFailed(action: String) {
    failedByAction.computeIfAbsent(action) { name ->
      Counter.builder("zone.ai.actions.failed")
        .tag("action", name)
        .description("Steps that failed, by action; actions are the planner's own, not anything a client names")
        .register(registry)
    }.increment()
  }

  /** One planner search that spent [spent] iterations. */
  fun planned(spent: Int, found: Boolean) {
    searches.getValue(if (found) "found" else "none").increment()
    iterations.record(spent.toDouble())
  }

  /** The agents of each detail level, counted over one whole sweep. */
  fun agents(counts: Map<AiDetail, Int>) {
    for ((detail, count) in agents) count.set(counts[detail] ?: 0)
  }
}
