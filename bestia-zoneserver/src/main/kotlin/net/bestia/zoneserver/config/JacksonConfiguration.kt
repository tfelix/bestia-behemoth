package net.bestia.zoneserver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfiguration {

  @Bean
  fun socketMapper(): ObjectMapper {
    return createBestiaMapper()
  }

  companion object {
    /**
     * This can be called externally to get a new Mapper configured for use
     * with Bestia.
     */
    fun createBestiaMapper(): ObjectMapper {
      return ObjectMapper()
          .registerModule(KotlinModule())
    }
  }
}