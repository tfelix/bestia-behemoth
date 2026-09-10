package net.bestia.zone.ai.domain

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Every domain a profile may declare, by id.
 *
 * A plain object and not a Spring registry, so profile validation stays constructible on its own - see
 * [AiDomainCatalogue]. The runtime halves are beans and Spring collects those; this is the half that has to
 * be reachable before a context exists.
 */
object AiDomains {

  private val byId: Map<String, AiDomainCatalogue> = listOf(BestiaDomain, TownsfolkDomain).associateBy { it.id }

  val ids: Set<String> get() = byId.keys

  fun of(id: String): AiDomainCatalogue? {
    return byId[id]
  }
}
