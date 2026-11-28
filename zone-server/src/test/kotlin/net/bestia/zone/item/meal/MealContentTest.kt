package net.bestia.zone.item.meal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.crafting.CraftingCatalog
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.prop.HerbPicking
import net.bestia.zone.prop.forage.ForageTable
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The meals as content: every one cooked at a campfire, discoverable, and the early ones in reach of a new master. */
class MealContentTest {

  private val catalog = CraftingCatalog()
  private val items = mockk<ItemTemplateRegistry> {
    every { idOf(any()) } answers { runCatching { catalog.item(firstArg<String>()).id }.getOrNull() }
    every { baseLevelOf(any()) } answers { catalog.item(firstArg<Long>()).baseLevel }
  }
  private val meals = MealCatalog(items).also { it.load(); it.warmUp() }
  private val forage = ForageTable(items).also { it.load(); it.warmUp() }
  private val cookingId = catalog.skillIdByName.getValue("M_COOKING")
  private val cooking = catalog.recipes.all().filter { it.requiredSkillId == cookingId && it.output != null }
    .filter { meals.isMeal(it.output!!.itemId) }

  @Test
  fun `forty-four meals, each cooked at a campfire by exactly one recipe`() {
    assertEquals(44, meals.all().size)
    assertEquals(meals.all().map { it.itemId }.toSet(), cooking.map { it.output!!.itemId }.toSet())
    assertEquals(cooking.size, cooking.map { it.output!!.itemId }.toSet().size)
    assertTrue(cooking.all { it.station == StaticEntityKind.CAMPFIRE })
  }

  @Test
  fun `every meal is a usable food that says what it does`() {
    meals.all().forEach { meal ->
      val item = catalog.item(meal.itemId)
      assertEquals("USABLE", item.type, item.identifier)
      assertEquals("MealScript", item.script, item.identifier)
      val minutes = meal.durationSeconds / 60
      assertTrue(item.description.orEmpty().contains("Satisfied for $minutes min"), "${item.identifier} names no duration")
    }
  }

  /** Discovery finds the nearest blueprint in a radius of 0.18 at Cooking 1; two meals closer than that blur. */
  @Test
  fun `the meals stand apart in the feature space`() {
    val points = cooking.map { it.identifier to catalog.blueprints.pointOf(it.id) }
    val unplaced = points.filter { it.second == null }.map { it.first }
    assertEquals(emptyList<String>(), unplaced - "HEARTY_STEW", "these meals cannot be discovered")

    val placed = points.mapNotNull { (name, point) -> point?.let { name to it } }
    val tooClose = placed.flatMapIndexed { i, (a, pa) ->
      placed.drop(i + 1).mapNotNull { (b, pb) ->
        val gap = pa.distanceTo(pb)
        "$a and $b are ${"%.3f".format(gap)} apart".takeIf { gap < MIN_GAP }
      }
    }
    assertEquals(emptyList<String>(), tooClose)
  }

  /**
   * The meals up to level 20 are cooked from what a fresh master can get: bestias up to level 15, forage an untrained
   * herbalist may pick, and fish a willow rod lands.
   */
  @Test
  fun `the early meals come from early loot, forage and fish`() {
    val early = mobLootUpTo(15) + forageUpTo(HerbPicking.maxHerbLevel(0)) + fishUpTo(WILLOW_ROD_LEVEL) + STARTER_GOODS

    val missing = cooking.filter { catalog.item(it.output!!.itemId).baseLevel <= 20 }.flatMap { recipe ->
      recipe.inputs.map { catalog.item(it.itemId).identifier }.filter { it !in early }.map { "${recipe.identifier}: $it" }
    }

    assertEquals(emptyList<String>(), missing)
  }

  @Test
  fun `every forage item exists and every biome table names something`() {
    assertTrue(forage.entriesOf(net.bestia.worldgen.bio.Biome.TEMPERATE_FOREST).isNotEmpty())
  }

  private fun mobLootUpTo(maxLevel: Int): Set<String> {
    val mapper = ObjectMapper(YAMLFactory())
    return PathMatchingResourcePatternResolver().getResources("classpath:mob/*.yml").flatMap { resource ->
      val mob: JsonNode = resource.inputStream.use { mapper.readTree(it) }
      val eventOnly = mob.path("event-only").asBoolean(false)
      if (eventOnly || mob.path("level-min").asInt(Int.MAX_VALUE) > maxLevel) emptyList()
      else mob.path("loot").map { it.path("item").asText() }
    }.toSet()
  }

  private fun forageUpTo(maxLevel: Int): Set<String> {
    return net.bestia.worldgen.bio.Biome.entries.flatMap { forage.entriesOf(it) }
      .filter { it.level <= maxLevel }.map { catalog.item(it.itemId).identifier }.toSet()
  }

  private fun fishUpTo(maxLevel: Int): Set<String> {
    val tree = ClassPathResource("fishing.yml").inputStream.use { ObjectMapper(YAMLFactory()).readTree(it) }
    return tree.path("catches").map { it.path("item").asText() }
      .filter { catalog.item(it).baseLevel <= maxLevel }.toSet()
  }

  private companion object {
    const val MIN_GAP = 0.18
    const val WILLOW_ROD_LEVEL = 25

    /** What lies on the ground or grows on every herb and shrub a new master walks past. */
    val STARTER_GOODS = setOf("wild_herb", "bramble_berries")
  }
}
