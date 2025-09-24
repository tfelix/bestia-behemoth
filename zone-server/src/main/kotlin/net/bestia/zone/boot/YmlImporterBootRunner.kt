package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.data.jpa.repository.JpaRepository

abstract class YmlImporterBootRunner<T, EntityT : Any>(
  private val importItemName: String,
  private val classpathFolder: String,
  private val repository: JpaRepository<EntityT, Long>,
  private val type: Class<T>
) : CommandLineRunner {

  data class ImportResult(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val notChanged: Int
  )

  override fun run(vararg args: String?) {
    LOG.info { "$importItemName import running ..." }

    val result = import()

    LOG.info { "$importItemName import finished: ${result.created} created, ${result.updated} updated, ${result.deleted} deleted." }
  }

  fun import(): ImportResult {
    val yamlItems = loadYmlItems()
    val existingEntities = repository.findAll().associateBy { getEntityIdentifier(it) }
    val currentIdentifiers = yamlItems.map { getYmlIdentifier(it) }.toSet()

    var created = 0
    var updated = 0
    var notChanged = 0

    for (yamlItem in yamlItems) {
      val existing = existingEntities[getYmlIdentifier(yamlItem)]

      if (existing == null) {
        val newEntity = newEntity(yamlItem)
        repository.save(newEntity)
        created++
      } else {
        if (tryUpdate(yamlItem, existing)) {
          repository.save(existing)
          updated++

        } else {
          notChanged++
        }
      }
    }

    val itemsToDelete = existingEntities.values.filter { getEntityIdentifier(it) !in currentIdentifiers }
    if (itemsToDelete.isNotEmpty()) {
      repository.deleteAll(itemsToDelete)
    }

    postImport(repository.findAll())

    return ImportResult(created, updated, itemsToDelete.size, notChanged)
  }

  private fun loadYmlItems(): List<T> {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val resourcePattern = "classpath:$classpathFolder/*.yml"
    val resolver = PathMatchingResourcePatternResolver()

    LOG.debug { "Loading ${type.simpleName} from $resourcePattern" }

    val resources = resolver.getResources(resourcePattern)

    return resources.map { resource ->
      objectMapper.readValue(resource.inputStream, type)
    }
  }

  protected abstract fun newEntity(dto: T): EntityT

  protected abstract fun getEntityIdentifier(entity: EntityT): String

  protected abstract fun getYmlIdentifier(dto: T): String

  protected abstract fun tryUpdate(dto: T, entity: EntityT): Boolean

  /**
   * Helper function to perform additional work on the generated entities.
   */
  protected open fun postImport(entities: List<EntityT>) {}

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}