package net.bestia.zone.crafting

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.boot.RecipeImporterBootRunner
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource

/**
 * Runs the real importer over the real `recipes.yml`, against identifier -> id maps read out of the real
 * `items.yml` and `skills.yml`.
 *
 * That is the whole point of the fixture: a mis-typed `item:` or `skill:` in a recipe is otherwise a boot
 * failure discovered by starting the server, and the recipe catalogue names things by identifier precisely so
 * it can be read - which only helps if the names are checked.
 */
class RecipeCatalogTest {

  private val itemRepository = mockk<ItemRepository>()
  private val skillRepository = mockk<SkillRepository>()
  private val registry = RecipeRegistry()

  init {
    val items = identifiers("items.yml", "items", "item-db-name")
    val skills = identifiers("skills.yml", "skills", "identifier")

    every { itemRepository.findByIdentifier(any()) } answers {
      items[firstArg()]?.let { id -> mockk<Item>().also { every { it.id } returns id } }
    }
    every { skillRepository.findByIdentifier(any()) } answers {
      skills[firstArg()]?.let { id -> mockk<Skill>().also { every { it.id } returns id } }
    }
  }

  private fun load() = RecipeImporterBootRunner(itemRepository, skillRepository, registry).run()

  @Test
  fun `the shipped recipes-yml resolves every item and skill it names`() {
    load()

    assertTrue(registry.size > 0, "recipes.yml should not be empty")
  }

  @Test
  fun `refining iron carries the ratio the catalogue documents`() {
    load()

    val ingot = registry.all().single { it.identifier == "IRON_INGOT" }

    assertEquals(RecipeEffect.PRODUCE, ingot.effect)
    assertEquals(1, ingot.output?.amount)
    assertEquals(2, ingot.inputs.size)
    // The furnace is the reason Ore Refinement is worth taking, so losing the station on this one recipe
    // would quietly turn refining into something done anywhere.
    assertEquals("FURNACE", ingot.station?.name)
  }

  @Test
  fun `every targeted recipe declares no output and every producing one does`() {
    load()

    registry.all().forEach { recipe ->
      if (recipe.effect == RecipeEffect.PRODUCE) {
        assertNotNull(recipe.output, "${recipe.identifier} is PRODUCE and must make something")
        assertTrue(!recipe.needsTarget, "${recipe.identifier} is PRODUCE and must not need a target")
      } else {
        assertNull(recipe.output, "${recipe.identifier} is ${recipe.effect} and must not make something")
        assertTrue(recipe.needsTarget, "${recipe.identifier} is ${recipe.effect} and must need a target")
      }
    }
  }

  /**
   * Every recipe costs something. A free craft would be an infinite item source, and the one recipe with an
   * empty input list allowed by [Recipe] is a shape for later rather than one the catalogue should use.
   */
  @Test
  fun `every shipped recipe consumes at least one input`() {
    load()

    registry.all().forEach { recipe ->
      assertTrue(recipe.inputs.isNotEmpty(), "${recipe.identifier} would be a free craft")
    }
  }

  @Test
  fun `recipes are reachable by the skill that unlocks them`() {
    load()

    registry.all().groupBy { it.requiredSkillId }.forEach { (skillId, expected) ->
      assertEquals(expected.toSet(), registry.forSkill(skillId).toSet())
    }
  }

  @Test
  fun `two recipes sharing an id fail the load`() {
    val one = recipe(id = 7, identifier = "ONE")
    val other = recipe(id = 7, identifier = "OTHER")

    assertThrows<IllegalArgumentException> { registry.load(listOf(one, other)) }
  }

  @Test
  fun `two recipes sharing an identifier fail the load`() {
    val one = recipe(id = 7, identifier = "SAME")
    val other = recipe(id = 8, identifier = "SAME")

    assertThrows<IllegalArgumentException> { registry.load(listOf(one, other)) }
  }

  private fun recipe(id: Long, identifier: String) = Recipe(
    id = id,
    identifier = identifier,
    effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(itemId = 1, amount = 1),
    inputs = listOf(Recipe.ItemStack(itemId = 2, amount = 1)),
    requiredSkillId = 3,
    requiredSkillLevel = 1,
    station = null,
    craftSeconds = 1f,
    baseSuccessChance = 1f
  )

  /** Reads `<listKey>[].{<idKey>: id, <identifierKey>: name}` out of a server resource. */
  private fun identifiers(resource: String, listKey: String, identifierKey: String): Map<String, Long> {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val tree = ClassPathResource(resource).inputStream.use { mapper.readTree(it) }

    return tree[listKey].associate { it[identifierKey].asText() to it["id"].asLong() }
  }
}
