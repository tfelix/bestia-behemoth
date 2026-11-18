package net.bestia.zone.fact.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.fact.FactRef

/**
 * What a played master knows, so a check on the tick is a set lookup. The rows of
 * [net.bestia.zone.fact.FactService] are the truth. Never synced: there is no window for it yet.
 */
class KnownFacts(facts: Set<FactRef> = emptySet()) : Component {

  private val facts = HashSet(facts)

  fun knows(fact: FactRef): Boolean {
    return fact in facts
  }

  fun add(fact: FactRef) {
    facts.add(fact)
  }
}
