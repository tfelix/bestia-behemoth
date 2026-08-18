package net.bestia.zone.crafting

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.boot.RecipeImporterBootRunner
import net.bestia.zone.ecs.battle.skill.KnownSkills
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

  /**
   * Every shipped recipe has to be reachable at the rank it asks for.
   *
   * The two halves of that - `recipes.yml`s `requiredSkill.level` and `items.yml`s `level` - are written in
   * different files by hand, and a recipe claiming Carpentry 1 is enough for a tier-15 output would be offered
   * to nobody and refused with `CRAFT_ITEM_TOO_ADVANCED`. Exactly the kind of dead catalogue entry that stays
   * invisible until a player complains.
   */
  @Test
  fun `every producing recipe is reachable at the rank it requires`() {
    load()

    val levelById = identifiers("items.yml", "items", "id", "level").mapKeys { it.key.toLong() }
    assertTrue(levelById.isNotEmpty(), "items.yml should declare levels")

    val bonuses = MasterCraftBonusService(skillRepository)

    registry.all().filter { it.effect == RecipeEffect.PRODUCE }.forEach { recipe ->
      val outputLevel = levelById[recipe.output!!.itemId]
      assertNotNull(outputLevel, "${recipe.identifier} names an output with no level")

      val atRequiredRank = KnownSkills(mutableMapOf(recipe.requiredSkillId to recipe.requiredSkillLevel))
      val reach = bonuses.maxItemLevel(atRequiredRank, recipe)

      assertTrue(
        outputLevel!! <= reach,
        "${recipe.identifier} makes a tier-$outputLevel item but its own required rank only reaches $reach"
      )
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

  /**
   * Reads one field of a server resource keyed by another, e.g. every item identifier to its id.
   *
   * Rows missing [valueKey] are dropped rather than defaulted, so an absent optional field cannot be mistaken
   * for a zero.
   */
  private fun identifiers(
    resource: String,
    listKey: String,
    identifierKey: String,
    valueKey: String = "id"
  ): Map<String, Long> {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val tree = ClassPathResource(resource).inputStream.use { mapper.readTree(it) }

    return tree[listKey]
      .filter { it.has(valueKey) }
      .associate { it[identifierKey].asText() to it[valueKey].asLong() }
  }
}
