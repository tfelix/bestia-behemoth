package net.bestia.zone.crafting

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.mining.OreCompanions
import net.bestia.zone.item.mining.OreYield
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The material chains, checked against the real catalogue: dropped or gathered, refined at a station, then used by a
 * recipe. The docs' crafting chains page is generated from the same files (`./gradlew craftGraph`), so these are
 * what keeps the page and the game saying the same thing.
 */
class CraftingChainTest {

  private val catalog = CraftingCatalog()

  @Test
  fun `every producing recipe can be learned, by being a starter recipe or by being discoverable`() {
    val stranded = catalog.recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .filterNot { catalog.starterKit.isStarterBlueprint(it.id) }
      .filter { catalog.blueprints.pointOf(it.id) == null }
      .map { it.identifier }

    assertTrue(
      stranded.isEmpty(),
      "These recipes are not in starter_kit.yml and have an input without craft-axes, so no master can ever " +
        "learn them: $stranded"
    )
  }

  @Test
  fun `an ore carries no craft axes, so refining it is never something to discover`() {
    val ores = catalog.items.filter { it.identifier.endsWith("_ore") }

    assertTrue(ores.isNotEmpty())
    assertEquals(emptyList<String>(), ores.filter { it.craftAxes.isNotEmpty() }.map { it.identifier })
  }

  @Test
  fun `no material is made out of itself, however many steps it takes`() {
    val madeFrom = catalog.recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .groupBy({ catalog.item(it.output!!.itemId).identifier }, { recipe -> recipe.inputs.map { catalog.item(it.itemId).identifier } })
      .mapValues { (_, lists) -> lists.flatten().toSet() }

    val done = mutableSetOf<String>()

    fun visit(item: String, path: List<String>) {
      require(item !in path) { "A recipe chain loops back on itself: ${(path + item).joinToString(" -> ")}" }
      if (!done.add(item)) return
      madeFrom[item]?.forEach { visit(it, path + item) }
    }

    madeFrom.keys.forEach { visit(it, emptyList()) }
  }

  @Test
  fun `every station a recipe is worked at can be built from a kit that a recipe makes`() {
    val made = catalog.recipes.all().mapNotNull { it.output }.map { catalog.item(it.itemId).identifier }.toSet()

    val without = catalog.recipes.stations().filter { "${it.name.lowercase()}_kit" !in made }

    assertEquals(emptyList<StaticEntityKind>(), without)
  }

  @Test
  fun `a Craftsman station kit is Carpentry, except the workbench which every master can build`() {
    val carpentry = catalog.skillIdByName.getValue("M_CARPENTRY")
    val basic = catalog.skillIdByName.getValue("M_BASIC_SKILL")

    val stations = listOf(
      StaticEntityKind.FURNACE, StaticEntityKind.FORGE, StaticEntityKind.TANNING_RACK, StaticEntityKind.LOOM,
      StaticEntityKind.GLASS_FURNACE, StaticEntityKind.GLASSBLOWING_BENCH, StaticEntityKind.ALCHEMY_LABORATORY,
      StaticEntityKind.TRANSMUTATION_WORKBENCH
    )
    stations.forEach { station ->
      val kit = kitRecipe(station)
      assertEquals(carpentry, kit.requiredSkillId, "${kit.identifier} should be a Carpentry recipe")
    }

    assertEquals(basic, kitRecipe(StaticEntityKind.WORKBENCH).requiredSkillId)
  }

  /** Whoever learns such a skill seldom has Carpentry, and the skill would open Handiwork with nothing to make. */
  @Test
  fun `a station kit outside the Craftsman tree is made with the skill that works at the station`() {
    val kitSkills = mapOf(
      StaticEntityKind.GEM_CUT_STATION to "M_GEM_CUTTING",
      StaticEntityKind.MINT to "M_MINTING",
    )

    kitSkills.forEach { (station, skill) ->
      val kit = kitRecipe(station)
      assertEquals(catalog.skillIdByName.getValue(skill), kit.requiredSkillId, "${kit.identifier} should be a $skill recipe")
      assertEquals(1, kit.requiredSkillLevel, "${kit.identifier} should come with the first level of $skill")
    }
  }

  @Test
  fun `the tanning rack, the loom and the two glass stations ask for no skill to run`() {
    val basic = catalog.skillIdByName.getValue("M_BASIC_SKILL")

    val tooDemanding = catalog.recipes.all()
      .filter { it.station in NO_SKILL_STATIONS }
      .filter { it.requiredSkillId != basic }
      .map { it.identifier }

    assertTrue(catalog.recipes.all().any { it.station in NO_SKILL_STATIONS })
    assertEquals(emptyList<String>(), tooDemanding)
  }

  /** What nobody can make has to be handed out by the world, or the whole chain above it is a dead end. */
  @Test
  fun `every raw material is handed out somewhere in the world`() {
    val made = catalog.recipes.all().mapNotNull { it.output }.map { catalog.item(it.itemId).identifier }.toSet()
    val used = catalog.recipes.all().flatMap { recipe -> recipe.inputs.map { catalog.item(it.itemId).identifier } }.toSet()

    val orphans = (used - made - worldSources()).sorted()

    assertEquals(emptyList<String>(), orphans, "nothing drops, grows, is mined or lies on the ground for these")
  }

  /** Mob drop tables, what props yield, fishing, the ore a pick breaks out and what rides along with it. */
  private fun worldSources(): Set<String> {
    fun text(pattern: String) = PathMatchingResourcePatternResolver().getResources(pattern)
      .joinToString("\n") { resource -> resource.inputStream.use { String(it.readAllBytes()) } }

    val byName = Regex("""item:\s*([a-z0-9_]+)""")
    val byId = Regex("""item-id:\s*(\d+)""")
    val drops = byName.findAll(text("classpath:mob/*.yml")).map { it.groupValues[1] }
    val fished = byName.findAll(text("classpath:fishing.yml")).map { it.groupValues[1] }
    val foraged = byName.findAll(text("classpath:forage.yml")).map { it.groupValues[1] }
    val props = byId.findAll(text("classpath:prop-kinds.yml")).map { catalog.item(it.groupValues[1].toLong()).identifier }
    val ores = OreYield.ITEM_BY_RESOURCE.values + OreCompanions.identifiers()

    return (drops + fished + foraged + props + ores).toSet()
  }

  private fun kitRecipe(station: StaticEntityKind): Recipe {
    val kitItem = catalog.item("${station.name.lowercase()}_kit").id

    return catalog.recipes.all().single { it.output?.itemId == kitItem }
  }

  private companion object {
    /** Stations where nobody needs a skill: the rack, the loom and the two glass stations. */
    val NO_SKILL_STATIONS = setOf(
      StaticEntityKind.TANNING_RACK, StaticEntityKind.LOOM,
      StaticEntityKind.GLASS_FURNACE, StaticEntityKind.GLASSBLOWING_BENCH
    )
  }
}
