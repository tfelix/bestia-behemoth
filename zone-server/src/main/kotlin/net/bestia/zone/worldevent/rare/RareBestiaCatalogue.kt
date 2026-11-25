package net.bestia.zone.worldevent.rare

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/** The named bestia of `rare_bestia.yml`. */
@Service
class RareBestiaCatalogue {

  private var rares: List<RareBestia> = emptyList()

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()
    val file = mapper.readValue(ClassPathResource(RESOURCE).inputStream, FileDto::class.java)

    require(file.rares.map { it.id }.toSet().size == file.rares.size) { "$RESOURCE names a rare twice" }
    require(file.rares.map { it.bestia }.toSet().size == file.rares.size) { "$RESOURCE names a species twice" }
    rares = file.rares

    LOG.info { "Loaded ${rares.size} rare bestia" }
  }

  fun all(): List<RareBestia> = rares

  fun ofSpecies(identifier: String): RareBestia? = rares.firstOrNull { it.bestia == identifier }

  private data class FileDto(val rares: List<RareBestia> = emptyList())

  private companion object {
    const val RESOURCE = "rare_bestia.yml"
    val LOG = KotlinLogging.logger { }
  }
}
