package net.bestia.zone.fishing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shipped data around fishing holds together: nothing a line brings up is a dead end, and every bait is bait
 * and can be had.
 */
class FishingDataTest {

  @Test
  fun `everything a line brings up is used somewhere`() {
    val usable = RECIPE_INPUTS + BAIT_SOURCES_OF_USE
    // Items a master opens rather than crafts with: what is inside them is the use.
    val opened = setOf("sealed_bottle")

    val unused = CATCHES.filter { it !in usable && it !in opened }

    assertTrue(unused.isEmpty(), "Catches no recipe takes: $unused")
  }

  @Test
  fun `every bait is BAIT ammo`() {
    val notBait = BAITS.filter { name ->
      val item = ITEMS.getValue(name)
      item["type"]?.asText() != "AMMO" || item["ammo-type"]?.asText() != "BAIT"
    }

    assertTrue(notBait.isEmpty(), "Baits in fishing.yml that items.yml does not make BAIT ammo: $notBait")
  }

  @Test
  fun `every bait can be had, crafted or foraged`() {
    val foraged = read("forage.yml")["biomes"].fields().asSequence()
      .flatMap { (_, yields) -> yields.map { it["item"].asText() } }
      .toSet()

    val unobtainable = BAITS.filter { it !in RECIPE_OUTPUTS && it !in foraged }

    assertTrue(unobtainable.isEmpty(), "Baits nobody can make or find: $unobtainable")
  }

  @Test
  fun `the scripts that open a catch name its id in items-yml`() {
    assertEquals(ITEMS.getValue("sealed_bottle")["id"].asLong(), SealedBottleScript.ITEM_ID)
    assertEquals(ITEMS.getValue(SunkenStrongbox.ITEM_IDENTIFIER)["id"].asLong(), SunkenStrongbox.ITEM_ID)
  }

  @Test
  fun `a strongbox holds gear from the country's band up to twenty over, never above eighty`() {
    assertEquals(1..28, SunkenStrongbox.gearLevels(1..8, above = 20, max = 80))
    assertEquals(40..68, SunkenStrongbox.gearLevels(40..48, above = 20, max = 80))
    assertEquals(60..80, SunkenStrongbox.gearLevels(70..78, above = 20, max = 80))
    // Corrupted ground starts above the cap; the box still holds the best it may.
    assertEquals(60..80, SunkenStrongbox.gearLevels(85..100, above = 20, max = 80))
  }

  private companion object {
    val MAPPER = ObjectMapper(YAMLFactory())

    fun read(resource: String): JsonNode {
      return ClassPathResource(resource).inputStream.use { MAPPER.readTree(it) }
    }

    val ITEMS: Map<String, JsonNode> by lazy {
      read("items.yml")["items"].associateBy { it["item-db-name"].asText() }
    }

    val FISHING: JsonNode by lazy { read("fishing.yml") }

    val CATCHES: List<String> by lazy { FISHING["catches"].map { it["item"].asText() } }

    val BAITS: List<String> by lazy { FISHING["baits"].map { it["item"].asText() } }

    /** A catch hooked as bait has a use too: the quiver. */
    val BAIT_SOURCES_OF_USE: Set<String> by lazy { BAITS.toSet() }

    val RECIPES: List<JsonNode> by lazy {
      listOf("recipes.yml", "gear_recipes.yml").flatMap { read(it)["recipes"].toList() }
    }

    val RECIPE_INPUTS: Set<String> by lazy {
      RECIPES.flatMap { recipe -> recipe["inputs"]?.map { it["item"].asText() }.orEmpty() }.toSet()
    }

    val RECIPE_OUTPUTS: Set<String> by lazy {
      RECIPES.mapNotNull { it["output"]?.get("item")?.asText() }.toSet()
    }
  }
}
