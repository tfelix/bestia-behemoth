package net.bestia.zone.ai.domain

import org.springframework.stereotype.Component

/**
 * Every domain a profile may declare, by id.
 *
 * Built from the catalogues alone, so profile validation stays constructible on its own - see
 * [AiDomainCatalogue]. Each domain's slice declares its catalogue as a bean; a test passes them in.
 */
@Component
class AiDomains(catalogues: List<AiDomainCatalogue>) {

  private val byId: Map<String, AiDomainCatalogue> = catalogues.associateBy { it.id }

  val ids: Set<String> get() = byId.keys

  fun of(id: String): AiDomainCatalogue? {
    return byId[id]
  }
}
