package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.ai.domain.AiDomainCatalogue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BestiaDomainConfiguration {

  @Bean
  fun bestiaDomainCatalogue(): AiDomainCatalogue {
    return BestiaDomain
  }
}
