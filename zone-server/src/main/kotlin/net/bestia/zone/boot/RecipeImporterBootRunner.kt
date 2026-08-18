package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads `recipes.yml` into [RecipeRegistry], resolving every item and skill *identifier* into the id the
 * runtime uses.
 *
 * The YAML names things rather than numbering them on purpose: a recipe that said `item: 9` would be
 * unreadable and would silently point at whatever item later took id 9. Ordered after the item importer
 * (100) and the skill importer (102), both of which it resolves against.
 */
@Component
@Order(104)
class RecipeImporterBootRunner(
  private val itemRepository: ItemRepository,
  private val skillRepository: SkillRepository,
  private val recipeRegistry: RecipeRegistry
) : CommandLineRunner {

  data class RecipesYmlFile(
    val recipes: List<RecipeDto> = emptyList()
  ) {
    data class RecipeDto(
      val id: Long,
      val identifier: String,
      val effect: String,
      val output: StackDto? = null,
      val inputs: List<StackDto> = emptyList(),
      val skill: SkillDto,
      val station: String? = null,
      val craftSeconds: Float,
      val baseSuccessChance: Float
    )

    data class StackDto(
      val item: String,
      val amount: Int
    )

    data class SkillDto(
      val skill: String,
      val level: Int
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply { registerKotlinModule() }

    val dto = ClassPathResource(RECIPES_RESOURCE).inputStream.use {
      objectMapper.readValue(it, RecipesYmlFile::class.java)
    }

    val recipes = dto.recipes.map { toRecipe(it) }
    recipeRegistry.load(recipes)

    LOG.info { "Recipe import finished: ${recipes.size} recipes loaded" }
  }

  private fun toRecipe(dto: RecipesYmlFile.RecipeDto): Recipe {
    val skill = skillRepository.findByIdentifier(dto.skill.skill)
      ?: error("Recipe '${dto.identifier}' requires unknown skill '${dto.skill.skill}'")

    return Recipe(
      id = dto.id,
      identifier = dto.identifier,
      effect = effectOf(dto),
      output = dto.output?.let { toStack(dto.identifier, it) },
      inputs = dto.inputs.map { toStack(dto.identifier, it) },
      requiredSkillId = skill.id,
      requiredSkillLevel = dto.skill.level,
      station = dto.station?.let { stationOf(dto.identifier, it) },
      craftSeconds = dto.craftSeconds,
      baseSuccessChance = dto.baseSuccessChance
    )
  }

  private fun toStack(recipeIdentifier: String, dto: RecipesYmlFile.StackDto): Recipe.ItemStack {
    val item = itemRepository.findByIdentifier(dto.item)
      ?: error("Recipe '$recipeIdentifier' references unknown item '${dto.item}'")

    return Recipe.ItemStack(itemId = item.id, amount = dto.amount)
  }

  private fun effectOf(dto: RecipesYmlFile.RecipeDto): RecipeEffect =
    RecipeEffect.entries.firstOrNull { it.name == dto.effect.uppercase() }
      ?: error(
        "Recipe '${dto.identifier}' declares unknown effect '${dto.effect}'; " +
            "expected one of ${RecipeEffect.entries.joinToString()}"
      )

  private fun stationOf(recipeIdentifier: String, name: String): StaticEntityKind =
    StaticEntityKind.entries.firstOrNull { it.name == name.uppercase() }
      ?: error("Recipe '$recipeIdentifier' names unknown station '$name'")

  companion object {
    private const val RECIPES_RESOURCE = "recipes.yml"

    private val LOG = KotlinLogging.logger { }
  }
}
