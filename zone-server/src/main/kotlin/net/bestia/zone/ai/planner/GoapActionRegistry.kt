package net.bestia.zone.ai.planner

import org.springframework.stereotype.Service

/**
 * Resolves [GoapAction] beans by id (the entries of an archetype's `actions` list), indexed with the
 * `associateBy` pattern. Add a new action by dropping in a new [GoapAction] bean.
 */
@Service
class GoapActionRegistry(
  actions: List<GoapAction>
) {

  private val actionsById = actions.associateBy { it.id }

  fun has(id: String): Boolean = actionsById.containsKey(id)

  fun get(id: String): GoapAction =
    actionsById[id] ?: throw IllegalArgumentException("Unknown GOAP action '$id'")

  fun resolve(ids: List<String>): List<GoapAction> = ids.map { get(it) }
}
