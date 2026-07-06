package net.bestia.zone.ai.goal

import org.springframework.stereotype.Service

/**
 * Resolves [Goal] beans by name (the `goals[].name` key of an archetype), indexed with the
 * `associateBy` pattern. Add a new goal by dropping in a new [Goal] bean.
 */
@Service
class GoalRegistry(
  goals: List<Goal>
) {

  private val goalsByName = goals.associateBy { it.name }

  fun has(name: String): Boolean = goalsByName.containsKey(name)

  fun get(name: String): Goal =
    goalsByName[name] ?: throw IllegalArgumentException("Unknown goal '$name'")
}
