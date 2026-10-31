package net.bestia.zone.bestia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.bestia.zone.boot.ItemImporterBootRunner
import net.bestia.zone.boot.MobImporterBootRunner
import net.bestia.zone.boot.SkillImporterBootRunner
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds every mob YAML against the other catalogues. Most species are generated from bestia-docs, and the boot
 * only skips an unknown loot item with a log line, so a typo there would ship unnoticed.
 */
class MobCatalogueTest {

  private val species = readSpecies()

  @Test
  fun `ids and identifiers are unique`() {
    assertEquals(species.size, species.map { it.id }.toSet().size, "two species share an id")
    assertEquals(species.size, species.map { it.identifier }.toSet().size, "two species share an identifier")
  }

  @Test
  fun `every learnset skill is in skills yml`() {
    val skills = readSkills().map { it.identifier }.toSet()

    val unknown = species.flatMap { mob -> mob.learnset.filter { it.skill !in skills }.map { "${mob.identifier}: ${it.skill}" } }

    assertEquals(emptyList(), unknown)
  }

  @Test
  fun `every loot item is in items yml`() {
    val items = readItems().map { it.identifier }.toSet()

    val unknown = species.flatMap { mob -> mob.loot.filter { it.itemIdentifier !in items }.map { "${mob.identifier}: ${it.itemIdentifier}" } }

    assertEquals(emptyList(), unknown)
  }

  @Test
  fun `a species a den may place lives in a biome or on corrupted land`() {
    val homeless = species.filter { !it.eventOnly && !it.corruptedOnly && it.habitat.isEmpty() }.map { it.identifier }

    // An empty habitat means anywhere, which only the blob-like starter species of old wanted.
    assertEquals(emptyList(), homeless)
  }

  @Test
  fun `a wild species fights with at most the active skills its tier allows`() {
    val tooMany = species.filter { mob -> mob.learnset.count { it.active } > MAX_ACTIVE_ATTACKS }.map { it.identifier }

    assertEquals(emptyList(), tooMany)
    assertTrue(species.any { mob -> mob.learnset.any { it.active } }, "no species fights with a skill at all")
  }

  private fun mapper(): ObjectMapper {
    return ObjectMapper(YAMLFactory())
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }

  private fun readSpecies(): List<MobImporterBootRunner.MobYmlDto> {
    return PathMatchingResourcePatternResolver().getResources("classpath:mob/*.yml").map { resource ->
      resource.inputStream.use { mapper().readValue(it, MobImporterBootRunner.MobYmlDto::class.java) }
    }
  }

  private fun readSkills(): List<SkillImporterBootRunner.SkillYmlDto> {
    return ClassPathResource("skills.yml").inputStream.use {
      mapper().readValue(it, SkillImporterBootRunner.SkillsYmlFile::class.java).skills
    }
  }

  private fun readItems(): List<ItemImporterBootRunner.ItemYamlDto> {
    return ClassPathResource("items.yml").inputStream.use {
      mapper().readValue(it, ItemImporterBootRunner.ItemsYmlFile::class.java).items
    }
  }

  private companion object {
    /** A boss, the highest tier, fights with four (`bestia_blueprint.yaml` tiers). */
    const val MAX_ACTIVE_ATTACKS = 4
  }
}
