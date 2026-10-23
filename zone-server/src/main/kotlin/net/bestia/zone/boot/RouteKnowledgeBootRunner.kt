package net.bestia.zone.boot

import net.bestia.zone.travel.knowledge.RouteKnowledgeStore
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads which routes each master knows to be blocked. After world generation, beside the other world data. */
@Component
@Order(7)
class RouteKnowledgeBootRunner(
  private val store: RouteKnowledgeStore,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    store.loadAll()
  }
}
