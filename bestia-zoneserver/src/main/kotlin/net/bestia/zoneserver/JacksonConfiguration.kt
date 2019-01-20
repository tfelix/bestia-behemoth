package net.bestia.zoneserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfiguration {

  @Qualifier("socketMapper")
  @Bean
  fun socketMapper(): ObjectMapper {
    val mapper = ObjectMapper()
        .registerModule(KotlinModule())
    return mapper
  }
}