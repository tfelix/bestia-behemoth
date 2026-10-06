package net.bestia.zone.ai.domain

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.townsfolk.domain.TownsfolkDomain

/** Every domain the server has, for a test that builds the AI without a Spring context. */
fun allAiDomains(): AiDomains {
  return AiDomains(listOf(BestiaDomain, TownsfolkDomain))
}
