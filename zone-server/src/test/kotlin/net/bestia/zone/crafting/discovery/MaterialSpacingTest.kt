package net.bestia.zone.crafting.discovery

import net.bestia.zone.crafting.CraftingCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Whether the materials leave room for the equipment to be told apart.
 *
 * A blueprint sits at the mean of its inputs, and a discovery finds the nearest one inside the crafter's radius
 * (0.18 at skill 1). Two tiers of one family that sit almost on top of each other make every recipe built from
 * them nearly the same point, so a player cannot tell which one a mix found. And a mean never leaves the hull of
 * its ingredients, so an axis with no material at one pole has no recipe out there.
 */
class MaterialSpacingTest {

  private val catalog = CraftingCatalog()

  @Test
  fun `every axis has materials at both poles`() {
    val vectors = catalog.axes.all().values

    CraftAxis.entries.forEach { axis ->
      assertTrue(vectors.any { it[axis] >= POLE }, "nothing is strongly ${axis.positivePole}")
      assertTrue(vectors.any { it[axis] <= -POLE }, "nothing is strongly ${axis.negativePole}")
    }
  }

  @Test
  fun `the tiers of one family stand apart`() {
    val tooClose = LADDERS.flatMap { (family, names) ->
      names.flatMapIndexed { i, a ->
        names.drop(i + 1).mapNotNull { b ->
          val gap = catalog.point(a).distanceTo(catalog.point(b))

          "$family: $a and $b are ${"%.2f".format(gap)} apart".takeIf { gap < MIN_TIER_GAP }
        }
      }
    }

    assertEquals(emptyList<String>(), tooClose)
  }

  @Test
  fun `no two materials are the same point`() {
    val ids = catalog.axes.all().keys.toList()

    val twins = ids.flatMapIndexed { i, a ->
      ids.drop(i + 1).filter { b -> catalog.axes.of(a)!!.distanceTo(catalog.axes.of(b)!!) < MIN_TWIN_GAP }
        .map { b -> "${catalog.item(a).identifier} and ${catalog.item(b).identifier}" }
    }

    assertEquals(emptyList<String>(), twins)
  }

  private companion object {
    const val POLE = 0.5
    const val MIN_TIER_GAP = 0.15
    const val MIN_TWIN_GAP = 0.05

    /** Tiers of one material family, lowest first. */
    val LADDERS = mapOf(
      "metal" to listOf(
        "tin_ingot", "copper_ingot", "bronze_ingot", "iron_ingot", "steel_ingot", "silver_ingot", "gold_bar",
        "quicksilver", "blackiron_ingot", "palladium_ingot", "mithril_ingot", "adamantium_ingot"
      ),
      "leather" to listOf("leather", "hardened_leather", "fine_leather", "drake_hide", "dragon_scale"),
      "cloth" to listOf("cloth", "silk_cloth", "shadowsilk", "starweave"),
      "wood" to listOf("plank", "yew_billet", "ironwood", "heartwood"),
      "gem" to listOf(
        "cut_amethyst", "cut_garnet", "cut_emerald", "cut_sapphire", "cut_ruby", "cut_onyx", "cut_pyrelith",
        "cut_diamond"
      ),
      "mana" to listOf(
        "mana_essence", "mana_dust", "mana_concentrate", "pure_mana_essence", "blue_mana_crystal",
        "yellow_mana_crystal", "red_mana_crystal", "star_core"
      ),
    )
  }
}
