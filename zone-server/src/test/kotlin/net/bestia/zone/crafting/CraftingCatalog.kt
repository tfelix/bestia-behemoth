package net.bestia.zone.crafting

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.boot.ItemImporterBootRunner
import net.bestia.zone.boot.RecipeImporterBootRunner
import net.bestia.zone.boot.StarterKitImporterBootRunner
import net.bestia.zone.crafting.discovery.BlueprintPoints
import net.bestia.zone.crafting.discovery.CraftVector
import net.bestia.zone.crafting.discovery.MaterialAxes
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import org.springframework.core.io.ClassPathResource

/**
 * The real `items.yml`, `recipes.yml` and `starter_kit.yml`, loaded the way the server loads them at boot, so a test
 * can ask questions of the whole crafting catalogue: the chains, the feature space and the blueprints in it.
 */
class CraftingCatalog {

  val items: List<ItemImporterBootRunner.ItemYamlDto>
  val recipes: RecipeRegistry = RecipeRegistry()
  val starterKit: StarterKit = StarterKit()
  val axes: MaterialAxes = MaterialAxes()
  val blueprints: BlueprintPoints = BlueprintPoints()

  private val itemById: Map<Long, ItemImporterBootRunner.ItemYamlDto>
  private val itemByName: Map<String, ItemImporterBootRunner.ItemYamlDto>

  /** Skill identifier (`M_CARPENTRY`) to id. */
  val skillIdByName: Map<String, Long>

  init {
    items = ClassPathResource("items.yml").inputStream.use {
      ObjectMapper(YAMLFactory()).registerKotlinModule()
        .readValue(it, ItemImporterBootRunner.ItemsYmlFile::class.java)
        .items
    }
    itemById = items.associateBy { it.id }
    itemByName = items.associateBy { it.identifier }

    val skills = skillIds()
    skillIdByName = skills
    val itemRepository = mockk<ItemRepository>()
    val skillRepository = mockk<SkillRepository>()
    every { itemRepository.findByIdentifier(any()) } answers {
      itemByName[firstArg()]?.let { dto -> mockk<Item>().also { every { it.id } returns dto.id } }
    }
    every { skillRepository.findByIdentifier(any()) } answers {
      skills[firstArg()]?.let { id -> mockk<Skill>().also { every { it.id } returns id } }
    }

    RecipeImporterBootRunner(itemRepository, skillRepository, recipes).run()
    StarterKitImporterBootRunner(recipes, starterKit).run()
    axes.load(items.filter { it.craftAxes.isNotEmpty() }.associate { it.id to CraftVector(it.craftAxes) })
    blueprints.load(recipes.all(), axes, starterKit)
  }

  fun item(id: Long): ItemImporterBootRunner.ItemYamlDto = itemById.getValue(id)

  fun item(name: String): ItemImporterBootRunner.ItemYamlDto = itemByName.getValue(name)

  /** The material at [name], as a point in the feature space. */
  fun point(name: String): CraftVector = axes.of(item(name).id) ?: error("$name has no craft-axes")

  private fun skillIds(): Map<String, Long> {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val tree = ClassPathResource("skills.yml").inputStream.use { mapper.readTree(it) }

    return tree["skills"].filter { it.has("id") }.associate { it["identifier"].asText() to it["id"].asLong() }
  }
}
