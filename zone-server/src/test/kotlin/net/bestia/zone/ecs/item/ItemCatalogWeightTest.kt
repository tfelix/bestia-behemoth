package net.bestia.zone.ecs.item

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * Reads the real `items.yml` and holds its weights against [WeightLimitCalculator], the same way
 * `RecipeCatalogTest` reads the real `recipes.yml`.
 *
 * Item weights and the carry limit are authored in different files, on the same scale, by hand. They drifted
 * once already: a third of the catalogue outweighed a fresh master entirely, so the first thing a new player
 * could not do was pick up the ore they had just mined. That is invisible from either file alone.
 */
class ItemCatalogWeightTest {

  private val fresh = WeightLimitCalculator().computeWeightLimit(strength = 10, vitality = 10, level = 1)
  private val weights = weightsByIdentifier()

  @Test
  fun `no catalogue item outweighs a fresh master`() {
    weights.forEach { (identifier, weight) ->
      assertTrue(
        weight <= fresh,
        "$identifier weighs $weight, more than a fresh master's whole limit of $fresh"
      )
    }
  }

  /**
   * Foraging is meant to be a reflex you indulge while walking somewhere else - see the `items.yml` section
   * comment. It stops being one the moment a herb costs a meaningful fraction of what an ore does.
   */
  @Test
  fun `the foraged plants stay reflex-cheap against an ore`() {
    val ore = weights.getValue("iron_ore")

    listOf("wild_herb", "bramble_berries", "reed_stalk").forEach { identifier ->
      val weight = weights.getValue(identifier)
      assertTrue(
        weight * 10 <= ore,
        "$identifier weighs $weight against an ore's $ore - too heavy to pick without thinking"
      )
    }
  }

  /**
   * The furnace has to pay for itself in carried weight, or there is no reason to refine before hauling the
   * ore home. `items.yml` and `recipes.yml` both state this ratio in prose; this is what keeps them honest.
   */
  @Test
  fun `refining iron is a weight gain`() {
    val input = 2 * weights.getValue("iron_ore") + weights.getValue("coal")
    val output = weights.getValue("iron_ingot")

    assertTrue(output < input, "refining $input of ore and coal into $output is not worth the furnace")
  }

  private fun weightsByIdentifier(): Map<String, Int> {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val tree = ClassPathResource("items.yml").inputStream.use { mapper.readTree(it) }

    return tree["items"].associate { it["item-db-name"].asText() to it["weight"].asInt() }
  }
}
