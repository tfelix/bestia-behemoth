package net.bestia.zone.crafting.gear

import net.bestia.zone.boot.ItemImporterBootRunner.ItemYamlDto
import net.bestia.zone.crafting.CraftingCatalog
import net.bestia.zone.crafting.discovery.CraftVector
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.stat.ItemLevel
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/**
 * Writes `gear_recipes.yml`: one recipe for every piece of the equipment catalogue (the last 346 items, ids 266 and up), so each can be
 * discovered and crafted. Run it with `./gradlew genGearRecipes`; never edit the output by hand, change the rules
 * here and run it again. `GearRecipesTest` fails when the file and these rules disagree.
 *
 * ### What a recipe is made of
 *
 * - **A main material**, from the ladder of what the item is made of (`material` in `items.yml`), at the tier of its
 *   level: tin, copper, bronze, iron, steel, then blackiron, palladium, mithril and adamantium for metal; the
 *   same for wood, leather, cloth and the rest. A name that says what it is made of wins: a bronze stiletto is
 *   bronze, an ironwood bo is ironwood. A legendary takes a main material of level 75 or more where the ladder
 *   has one, because a forged roll is capped at its main material's level plus 20.
 * - **A kit** that makes the type: a keen thing for a dagger or a sword, a heavy blunt one for a mace or a hammer,
 *   a cord for a whip or a sling. Without it a sword, a mace and a hammer of one metal would be the same point.
 * - **A signature** for what the name promises: a poison dagger takes deathcap, an ember staff takes an ember core,
 *   a sapphire pendant takes a cut sapphire. Items whose names promise nothing take a quiet extra from a small
 *   pool, which is what tells the plain ones apart.
 *
 * Of those candidates it takes the one **farthest from every blueprint already placed** in the same skill, so
 * discovery has room: the radius at skill 1 is 0.18 and a recipe is only found when the mix is nearest to it.
 * The skill is the item's kind, the level is `ceil(item level / 10)`, the success chance and the time are the
 * design docs' curves, and a metal main material means the forge where anything else means the workbench.
 */
object GearRecipeGenerator {

  const val FIRST_ID = 200L

  /** The first id of the equipment catalogue, the tail of `items.yml`. Moves whenever items are added before it. */
  const val CATALOGUE_FIRST_ID = 266L
  const val OUTPUT_FILE = "gear_recipes.yml"

  /** What `GearRecipesTest` asks of the nearest neighbour of every blueprint. */
  const val MIN_SPACING = 0.20

  class Plan(
    val item: ItemYamlDto,
    val itemLevel: Int,
    val skill: String,
    val station: String,
    val inputs: List<Pair<String, Int>>,
    val spacing: Double,
  ) {
    val identifier: String get() = item.identifier.uppercase()
    val skillLevel: Int get() = ceil(itemLevel / 10.0).toInt().coerceIn(1, 10)

    /** The docs' construction curve, which goes below zero above level 70 on purpose. */
    val baseChance: Float get() = (0.90 - max(0, itemLevel - 10) * 0.015).toFloat()

    /** The docs' `1.2 x level^2`. */
    val seconds: Float get() = (1.2 * itemLevel * itemLevel).toFloat()
  }

  fun craftable(item: ItemYamlDto): Boolean = item.type.uppercase() == "EQUIP" && item.id >= CATALOGUE_FIRST_ID

  /** One item and the recipe it has been given so far. */
  private class Chosen(
    val item: ItemYamlDto,
    val itemLevel: Int,
    val skill: String,
    var inputs: List<Pair<String, Int>>,
    var point: CraftVector,
  )

  /**
   * Plans every craftable piece against the catalogue as it stands without any gear recipe.
   *
   * First by level, each piece taking the candidate farthest from everything placed so far. Then a few sweeps in which
   * the pieces with the least room are taken out and offered every candidate again against all the others, the ones
   * placed after them included: the first pass cannot know that a later steel halberd will want the same corner.
   */
  fun plan(catalog: CraftingCatalog = CraftingCatalog(includeGear = false)): List<Plan> {
    val skillNames = catalog.skillIdByName.entries.associate { it.value to it.key }
    val fixed = HashMap<String, MutableList<CraftVector>>()
    catalog.recipes.all().forEach { recipe ->
      val point = catalog.blueprints.pointOf(recipe.id) ?: return@forEach
      fixed.getOrPut(skillNames.getValue(recipe.requiredSkillId)) { ArrayList() }.add(point)
    }

    val gear = catalog.items.filter(::craftable).map { it to itemLevelOf(it) }.sortedWith(compareBy({ it.second }, { it.first.id }))
    val chosen = ArrayList<Chosen>()
    for ((item, itemLevel) in gear) {
      val skill = if (item.weaponType != null) "M_FORGE_WEAPON" else "M_FORGE_ARMOR"
      val others = fixed.getOrDefault(skill, emptyList<CraftVector>()) + chosen.filter { it.skill == skill }.map { it.point }
      val (inputs, point, _) = best(catalog, item, itemLevel, others)
      chosen.add(Chosen(item, itemLevel, skill, inputs, point))
    }

    repeat(SWEEPS) {
      for (piece in chosen.sortedBy { room(it, chosen, fixed) }.take(CROWDED_AT_MOST)) {
        if (room(piece, chosen, fixed) >= ROOMY) break
        val (inputs, point, away) = best(catalog, piece.item, piece.itemLevel, othersOf(piece, chosen, fixed), crowded = true)
        if (away > room(piece, chosen, fixed)) {
          piece.inputs = inputs
          piece.point = point
        }
      }
    }

    return chosen.map { piece ->
      val station = if (catalog.item(piece.inputs.first().first).material == ItemMaterial.METAL) "FORGE" else "WORKBENCH"

      Plan(piece.item, piece.itemLevel, piece.skill, station, piece.inputs, room(piece, chosen, fixed))
    }
  }

  private fun othersOf(piece: Chosen, chosen: List<Chosen>, fixed: Map<String, List<CraftVector>>): List<CraftVector> =
    fixed.getOrDefault(piece.skill, emptyList()) + chosen.filter { it !== piece && it.skill == piece.skill }.map { it.point }

  /** How far a piece's blueprint is from the nearest other one of its skill. */
  private fun room(piece: Chosen, chosen: List<Chosen>, fixed: Map<String, List<CraftVector>>): Double =
    othersOf(piece, chosen, fixed).minOfOrNull { it.distanceTo(piece.point) } ?: 9.0

  private fun best(
    catalog: CraftingCatalog,
    item: ItemYamlDto,
    itemLevel: Int,
    others: List<CraftVector>,
    crowded: Boolean = false,
  ): Triple<List<Pair<String, Int>>, CraftVector, Double> {
    var best: Triple<List<Pair<String, Int>>, CraftVector, Double>? = null
    var bestScore = Double.NEGATIVE_INFINITY
    // The plain candidates first; only an item that finds no room among them is offered a second extra.
    for (offerSecond in if (crowded) listOf(true) else listOf(false, true)) {
      if (offerSecond && !crowded && bestScore - 0.002 * (best?.first?.size ?: 0) >= ROOMY) break
      for (inputs in candidatesFor(catalog, item, itemLevel, offerSecond)) {
        val point = CraftVector.mixOf(inputs.map { (name, amount) -> catalog.point(name) to amount })
        val away = others.minOfOrNull { it.distanceTo(point) } ?: 9.0
        val score = away - 0.002 * inputs.size
        if (score > bestScore) {
          bestScore = score
          best = Triple(inputs, point, away)
        }
      }
    }

    return checkNotNull(best) { "${item.identifier} has no candidate recipe" }
  }

  fun render(plans: List<Plan>): String {
    val out = StringBuilder()
    out.appendLine("# GENERATED by `./gradlew genGearRecipes` from items.yml and the rules in GearRecipeGenerator. Do not edit.")
    out.appendLine("#")
    out.appendLine("# One recipe for every piece of the equipment catalogue: a main material at the item's tier, a kit that")
    out.appendLine("# makes the type, and a signature for what its name promises. Weapons are Forge Weapon and armour is Forge")
    out.appendLine("# Armor, at rank ceil(level / 10); metal is worked at the forge and everything else at the workbench. The")
    out.appendLine("# chance is the docs' 0.90 - max(0, level - 10) * 0.015, so it goes below zero above level 70 and only")
    out.appendLine("# skill, stats and bonuses lift it; the time is 1.2 * level^2 seconds.")
    out.appendLine("recipes:")
    plans.forEachIndexed { index, plan ->
      out.appendLine()
      out.appendLine("  - id: ${FIRST_ID + index}")
      out.appendLine("    identifier: ${plan.identifier}")
      out.appendLine("    effect: PRODUCE")
      out.appendLine("    output: { item: ${plan.item.identifier}, amount: 1 }")
      out.appendLine("    inputs:")
      plan.inputs.forEach { (name, amount) -> out.appendLine("      - { item: $name, amount: $amount }") }
      out.appendLine("    skill: { skill: ${plan.skill}, level: ${plan.skillLevel} }")
      out.appendLine("    station: ${plan.station}")
      out.appendLine("    craftSeconds: ${"%.1f".format(java.util.Locale.ROOT, plan.seconds)}")
      out.appendLine("    baseSuccessChance: ${"%.3f".format(java.util.Locale.ROOT, plan.baseChance)}")
    }

    return out.toString()
  }

  fun itemLevelOf(item: ItemYamlDto): Int = ItemLevel.of(item.baseLevel, item.toStatSheet().fixedStats())

  // ---- candidates --------------------------------------------------------------------------------------------

  private fun candidatesFor(catalog: CraftingCatalog, item: ItemYamlDto, itemLevel: Int, crowded: Boolean): List<List<Pair<String, Int>>> {
    val kit = kitOf(item)
    val extras = signaturesOf(catalog, item, crowded)

    return mainsOf(catalog, item, itemLevel).flatMap { main ->
      amountsOf(item).flatMap { amount ->
        listOf(1, 2).flatMap { kitScale ->
          if (kit.isEmpty() && kitScale == 2) return@flatMap emptyList()
          extras.map { extra -> merge(listOf(main to amount) + kit.map { (name, n) -> name to n * kitScale } + extra) }
        }
      }
    }
  }

  /** The same item named twice becomes one stack; the first entry stays first, because it is the main material. */
  private fun merge(parts: List<Pair<String, Int>>): List<Pair<String, Int>> {
    val amounts = LinkedHashMap<String, Int>()
    parts.forEach { (name, amount) -> amounts.merge(name, amount, Int::plus) }

    return amounts.toList()
  }

  private fun tokensOf(item: ItemYamlDto): Set<String> = item.identifier.split('_').toSet()

  private fun mainsOf(catalog: CraftingCatalog, item: ItemYamlDto, itemLevel: Int): List<String> {
    val tokens = tokensOf(item)
    MAIN_BY_WORD.entries.firstOrNull { (word, _) -> word in tokens }?.let { hit ->
      // A legendary named for iron is not made of iron: the name still says its theme, the ladder says its tier.
      if (itemLevel < LEGENDARY || catalog.item(hit.value).baseLevel >= LEGENDARY_NAMED) return listOf(hit.value)
    }

    val ladder = ladderOf(item).filter { catalog.axes.of(catalog.item(it).id) != null }
    val legendary = if (itemLevel >= LEGENDARY) ladder.filter { catalog.item(it).baseLevel >= LEGENDARY_MAIN } else emptyList()
    val pool = legendary.ifEmpty { ladder }.sortedBy { catalog.item(it).baseLevel }
    val fit = pool.indexOfLast { catalog.item(it).baseLevel <= itemLevel + REACH }.coerceAtLeast(0)

    val candidates = listOfNotNull(pool.getOrNull(fit), pool.getOrNull(fit - 1)).distinct()
    if (itemLevel < LEGENDARY) return candidates

    // A legendary keeps to the top of its ladder: 75 and up where it has that, else its highest tier.
    val floor = if (legendary.isNotEmpty()) LEGENDARY_MAIN else catalog.item(pool.last()).baseLevel

    return candidates.filter { catalog.item(it).baseLevel >= floor }.ifEmpty { listOfNotNull(pool.lastOrNull()) }
  }

  private fun ladderOf(item: ItemYamlDto): List<String> = when (item.material) {
    ItemMaterial.METAL ->
      if (item.equipSlot == "ACCESSORY_1") JEWEL_METALS else STRUCTURAL_METALS
    ItemMaterial.WOOD -> listOf("plank", "yew_billet", "ironwood", "heartwood")
    ItemMaterial.LEATHER -> listOf("leather", "hardened_leather", "fine_leather", "drake_hide", "dragon_scale")
    ItemMaterial.CLOTH -> listOf("cloth", "silk_cloth", "shadowsilk", "starweave")
    ItemMaterial.PAPER -> listOf("parchment", "fine_leather", "mana_paper")
    ItemMaterial.STONE ->
      if (item.equipSlot == "ACCESSORY_1") listOf("bone_shard", "quarried_stone", "cut_stone", "lodestone", "obsidian")
      else listOf("loose_stone", "quarried_stone", "cut_stone", "obsidian", "lodestone", "wyrm_bone")
    ItemMaterial.GLASS -> listOf("glass", "lens")
    ItemMaterial.CRYSTAL -> listOf("mana_crystal", "blue_mana_crystal", "cut_amethyst", "cut_sapphire", "cut_ruby", "cut_diamond", "star_core")
    ItemMaterial.PLANT -> listOf("rope", "reed_stalk", "thornvine", "medicinal_herb", "glowcap")
    ItemMaterial.FOOD -> listOf("leather")
  }

  private fun amountsOf(item: ItemYamlDto): List<Int> = when (item.equipSlot) {
    "ARMOR" -> listOf(4, 5)
    "GARMENT" -> listOf(3, 4)
    "HEAD_UPPER", "FOOTGEAR" -> listOf(2, 3)
    "HEAD_MID", "ACCESSORY_1" -> listOf(1, 2)
    "LEFT_HAND" -> listOf(3, 4)
    "LIGHT_SOURCE" -> listOf(2, 3)
    else -> if (item.twoHanded || item.weaponType in TWO_HANDED) listOf(3, 4) else listOf(2, 3)
  }

  private fun kitOf(item: ItemYamlDto): Map<String, Int> {
    item.weaponType?.let { return WEAPON_KITS[it.name].orEmpty() }

    return when (item.equipSlot) {
      "LIGHT_SOURCE" -> mapOf("lamp_oil" to 1, "glass" to 1)
      "HEAD_MID", "ACCESSORY_1" -> emptyMap()
      else -> when (item.armorType?.name) {
        "LIGHT" -> mapOf("leather" to 1)
        "MEDIUM" -> mapOf("leather" to 1, "nails" to 1)
        "HEAVY" -> mapOf("leather" to 2, "nails" to 2)
        else -> emptyMap()
      }
    }
  }

  /** The options for the extra: what the name promises, or a quiet one from the pool, or nothing. */
  private fun signaturesOf(catalog: CraftingCatalog, item: ItemYamlDto, crowded: Boolean): List<List<Pair<String, Int>>> {
    val name = item.identifier
    val tokens = tokensOf(item)

    val quiet = QUIET.filter { catalog.axes.of(catalog.item(it).id) != null }
    if (item.equipSlot == "ACCESSORY_1") {
      GEM_BY_WORD.entries.firstOrNull { (word, _) -> word in tokens }?.let { gem ->
        val forced = listOf(gem.value to 1)
        return listOf(forced) + if (crowded) quiet.map { forced + (it to 1) } else emptyList()
      }
    }

    val themed = THEMES.filter { (words, _) -> words.any { word -> name.contains(word) } }.flatMap { it.second }.distinct()
      .filter { catalog.axes.of(catalog.item(it).id) != null }
    if (themed.isNotEmpty()) {
      val single = themed.flatMap { listOf(listOf(it to 1), listOf(it to 2)) }

      return single + if (crowded) themed.flatMap { promise -> quiet.map { listOf(promise to 1, it to 1) } } else emptyList()
    }

    val single = listOf(emptyList<Pair<String, Int>>()) + quiet.map { listOf(it to 1) }

    return single + if (crowded) quiet.flatMapIndexed { i, a -> quiet.drop(i + 1).map { b -> listOf(a to 1, b to 1) } } else emptyList()
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val plans = plan()
    val target = File(args.firstOrNull() ?: "src/main/resources/$OUTPUT_FILE")
    target.writeText(render(plans))

    val worst = plans.sortedBy { it.spacing }.take(8)
    println("Wrote ${plans.size} recipes to ${target.absolutePath}")
    println("Closest to a neighbour when placed: " + worst.joinToString { "${it.identifier} %.3f".format(it.spacing) })
  }

  // ---- the rules ---------------------------------------------------------------------------------------------

  private const val SWEEPS = 6
  private const val CROWDED_AT_MOST = 80

  /** The spacing at which an item is content with the plain candidates and is not offered a second extra. */
  private const val ROOMY = 0.26

  private const val LEGENDARY = 81
  private const val LEGENDARY_MAIN = 75

  /** The lowest named main material a legendary keeps: below this it is made from its ladder instead. */
  private const val LEGENDARY_NAMED = 60

  /** How far above an item's level its main material may sit and still be the one for its tier. */
  private const val REACH = 12

  private val STRUCTURAL_METALS = listOf(
    "tin_ingot", "copper_ingot", "bronze_ingot", "iron_ingot", "steel_ingot", "blackiron_ingot", "palladium_ingot",
    "mithril_ingot", "adamantium_ingot"
  )

  private val JEWEL_METALS = listOf("tin_ingot", "copper_ingot", "silver_ingot", "gold_bar", "palladium_ingot", "mithril_ingot")

  private val TWO_HANDED = setOf(
    net.bestia.zone.battle.gear.WeaponType.GREATSWORD, net.bestia.zone.battle.gear.WeaponType.GREATAXE,
    net.bestia.zone.battle.gear.WeaponType.HAMMER, net.bestia.zone.battle.gear.WeaponType.POLEARM,
    net.bestia.zone.battle.gear.WeaponType.STAFF, net.bestia.zone.battle.gear.WeaponType.BOW,
    net.bestia.zone.battle.gear.WeaponType.CROSSBOW, net.bestia.zone.battle.gear.WeaponType.INSTRUMENT
  )

  /** What makes a type a type, from the docs' axes: sword keen, mace blunt, hammer massive, whip a cord. */
  private val WEAPON_KITS = mapOf(
    "DAGGER" to mapOf("flint" to 1),
    "SWORD" to mapOf("flint" to 1),
    "AXE" to mapOf("stick" to 1, "flint" to 1),
    "GREATAXE" to mapOf("stick" to 2, "flint" to 1),
    "GREATSWORD" to mapOf("flint" to 1, "leather" to 1),
    "MACE" to mapOf("loose_stone" to 2),
    "HAMMER" to mapOf("cut_stone" to 2),
    "SPEAR" to mapOf("stick" to 2, "sharp_fang" to 1),
    "POLEARM" to mapOf("stick" to 3, "flint" to 1),
    "FIST" to mapOf("leather" to 2),
    "WHIP" to mapOf("rope" to 2),
    "STAFF" to mapOf("stick" to 2),
    "WAND" to mapOf("stick" to 1, "mana_dust" to 1),
    "TOME" to mapOf("parchment" to 2, "mana_ink" to 1),
    "BOW" to mapOf("rope" to 1, "feather" to 1),
    "CROSSBOW" to mapOf("nails" to 2, "rope" to 1),
    "INSTRUMENT" to mapOf("rope" to 1, "plank" to 1),
    "THROWN" to mapOf("feather" to 1),
    "SLING" to mapOf("rope" to 2),
    "TOOL" to mapOf("stick" to 1),
  )

  /** A word in the name that says what it is made of. The first match wins, so the order is the precedence. */
  private val MAIN_BY_WORD = linkedMapOf(
    "blackiron" to "blackiron_ingot", "bronze" to "bronze_ingot", "steel" to "steel_ingot", "iron" to "iron_ingot",
    "copper" to "copper_ingot", "tin" to "tin_ingot", "silver" to "silver_ingot", "mithril" to "mithril_ingot",
    "ironwood" to "ironwood", "heartwood" to "heartwood", "yew" to "yew_billet", "wicker" to "reed_stalk",
    "straw" to "reed_stalk", "rope" to "rope", "bone" to "bone_shard", "horn" to "horn", "coral" to "coral",
    "obsidian" to "obsidian", "lodestone" to "lodestone", "shadowsilk" to "shadowsilk", "silken" to "silk_cloth",
    "silk" to "silk_cloth", "thornvine" to "thornvine", "wyrmbone" to "wyrm_bone",
  )

  /** A stone the name asks for, on an accessory. */
  private val GEM_BY_WORD = linkedMapOf(
    "garnet" to "cut_garnet", "sapphire" to "cut_sapphire", "emerald" to "cut_emerald", "ruby" to "cut_ruby",
    "onyx" to "cut_onyx", "opal" to "cut_opal", "jade" to "cut_jade", "tigers" to "cut_tigers_eye",
    "diamond" to "cut_diamond", "amethyst" to "cut_amethyst", "pyrelith" to "cut_pyrelith",
  )

  /** Words in a name and the materials that carry what they promise. */
  private val THEMES: List<Pair<List<String>, List<String>>> = listOf(
    listOf("poison", "venom", "lancet", "plague", "caustic", "serpent", "barbed", "viper") to listOf("deathcap", "venom_sac", "basic_poison"),
    listOf("ember", "flame", "fire", "flamberge", "sun", "dawn", "forge", "carbide") to listOf("ember_core"),
    listOf("frost", "moon", "winter") to listOf("frost_crystal"),
    listOf("holy", "saint", "templar", "warden", "psalm", "hymnal", "liturgy", "mending", "priest", "cathedral", "pilgrim", "oath") to listOf("holy_water", "silver_ingot", "sun_herb"),
    listOf("storm", "tempest", "wind", "skyrender", "seven_leagues", "wandering") to listOf("storm_feather"),
    listOf("shadow", "night", "veil", "hollow", "quiet", "grave", "dusk", "eclipse", "silent", "assassin", "whisper") to listOf("shadow_shard", "void_essence", "ectoplasm"),
    listOf("star", "oracle", "astral", "lode") to listOf("star_core", "cut_diamond"),
    listOf("gear", "clockwork", "tinker", "artificer", "brass", "conduit", "windlass", "repeating", "hours", "mirror") to listOf("cog", "clockwork_core"),
    listOf("ley", "rune", "ward", "circle", "magister", "mana", "arcane", "spark", "sage", "conjur") to listOf("mana_dust", "mana_essence", "pure_mana_essence", "mana_ink"),
    listOf("stone", "mountain", "quarry", "miner", "pick", "ore", "earth", "anvil", "deep") to listOf("cut_stone", "lodestone", "obsidian"),
    listOf("wyrm", "drake", "dragon") to listOf("wyrm_bone", "drake_hide", "dragon_scale"),
    listOf("herb", "heal", "growth", "forest", "ranger", "trail", "hunt", "thorn", "wood", "ghillie") to listOf("medicinal_herb", "thornvine", "glowcap"),
    listOf("tide", "sea", "pearl") to listOf("sea_pearl", "coral"),
    listOf("lantern", "lamp") to listOf("lamp_oil", "glowcap", "lens"),
  )

  /** What a plain item may carry to stand apart: cheap, common and quiet. */
  private val QUIET = listOf(
    "feather", "horn", "bone_shard", "resin", "stick", "leather", "cloth", "nails", "rope", "clay", "salt", "coal",
    "soft_fur", "reed_stalk", "lamp_oil", "mana_essence",
  )
}
