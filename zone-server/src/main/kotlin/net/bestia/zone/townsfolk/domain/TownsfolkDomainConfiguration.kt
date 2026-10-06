package net.bestia.zone.townsfolk.domain

import net.bestia.zone.ai.domain.AiDomainCatalogue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TownsfolkDomainConfiguration {

  @Bean
  fun townsfolkDomainCatalogue(): AiDomainCatalogue {
    return TownsfolkDomain
  }
}
