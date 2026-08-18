package net.bestia.zone.crafting

import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.skill.SkillRepository
import org.springframework.stereotype.Service

/**
 * What a crafter's invested skill levels are worth, straight off the design docs' per-level tables.
 *
 * ### Why these are not `PassiveSkillScript`s
 *
 * [net.bestia.zone.battle.skill.passive.PassiveSkillScript] can only mutate a
 * [net.bestia.zone.battle.status.StatusValueRecalcContext] - six attributes, speed and the regeneration
 * modifiers. `TINKERER`, `WEAPONRY_RESEARCH` and `MASTER_SMITH` change none of those; they change a
 * *success chance*, which lives nowhere near a status value. Binding them as passives would give each a
 * script whose `apply` did nothing, which reads as "implemented" and is the failure worth not shipping.
 *
 * ### Reading levels
 *
 * Off the caster's live [KnownSkills], which is what `ActivateSkillHandler` already validates against, so
 * this asks no database on the tick thread. Skill *ids* come from the repository once, lazily and by
 * identifier, on the precedent of `EnvironmentalExposureSystem` - the numeric ids in `skills.yml` are
 * content and this is code.
 *
 * ### One table per docs row
 *
 * Every table below is indexed by `level - 1` and is as long as the skill's `maxLevel` in
 * `master_skill_tree.yml`. Lookup clamps rather than throwing, because a level past the end of a table means
 * the catalogue raised a maxLevel, and that should degrade to the best documented value rather than kill a
 * craft in progress.
 */
@Service
class MasterCraftBonusService(
  private val skills: SkillRepository
) {

  private val carpentryId: Long? by lazy { skills.findByIdentifier(CARPENTRY)?.id }
  private val tinkererId: Long? by lazy { skills.findByIdentifier(TINKERER)?.id }
  private val itemCustomizationId: Long? by lazy { skills.findByIdentifier(ITEM_CUSTOMIZATION)?.id }
  private val oreRefinementId: Long? by lazy { skills.findByIdentifier(ORE_REFINEMENT)?.id }
  private val weaponryResearchId: Long? by lazy { skills.findByIdentifier(WEAPONRY_RESEARCH)?.id }
  private val masterSmithId: Long? by lazy { skills.findByIdentifier(MASTER_SMITH)?.id }
  private val cookingId: Long? by lazy { skills.findByIdentifier(COOKING)?.id }
  private val weaponRepairId: Long? by lazy { skills.findByIdentifier(WEAPON_REPAIR)?.id }
  private val forgeWeaponId: Long? by lazy { skills.findByIdentifier(FORGE_WEAPON)?.id }
  private val forgeArmorId: Long? by lazy { skills.findByIdentifier(FORGE_ARMOR)?.id }
  private val upgradeEquipmentId: Long? by lazy { skills.findByIdentifier(UPGRADE_EQUIPMENT)?.id }

  /**
   * The chance [recipe] succeeds for this crafter, clamped to 0..1.
   *
   * Which bonuses apply is decided by the recipe's *required skill*, because that is what says which kind of
   * work this is - a recipe carrying its own category field would be a second answer to the same question,
   * and the two would drift.
   */
  fun successChance(known: KnownSkills?, recipe: Recipe): Float {
    val base = recipe.baseSuccessChance
    if (known == null) return base.coerceIn(0f, 1f)

    val bonus = when (recipe.requiredSkillId) {
      cookingId -> at(COOKING_SUCCESS, known.levelIn(cookingId))

      carpentryId -> at(CARPENTRY_SUCCESS, known.levelIn(carpentryId)) + masterCraftsmanSuccess(known)

      itemCustomizationId ->
        at(CUSTOMIZATION_SUCCESS, known.levelIn(itemCustomizationId)) + masterCraftsmanSuccess(known)

      oreRefinementId -> at(REFINEMENT_SUCCESS, known.levelIn(oreRefinementId))

      // Forging draws on both smithing passives plus the forge skill's own level, which the docs give no
      // table of its own - so it contributes a small per-level step rather than nothing, since a level 10
      // forge skill that bought only the right to place a forge would be a strange thing to invest ten
      // points in.
      forgeWeaponId, forgeArmorId ->
        forgingBonus(known) + at(FORGE_SUCCESS, known.levelIn(recipe.requiredSkillId))

      upgradeEquipmentId -> upgradeBonus(known)

      // A repair's chance is the recipe's own. The docs give Weapon Repair a max item level and no success
      // table, and inventing one here would be inventing content.
      else -> 0f
    }

    return (base + bonus).coerceIn(0f, 1f)
  }

  /**
   * How long [recipe] actually takes, after the construction-time reduction - the only thing in the docs that
   * shortens a craft, and the reason [Recipe.craftSeconds] is a base rather than a duration.
   *
   * Cooking has its own reduction table and does not stack with Master Craftsman: the docs put the two in
   * different trees, so a crafter holding both would otherwise get a discount the design never granted.
   */
  fun craftSeconds(known: KnownSkills?, recipe: Recipe): Float {
    if (known == null) return recipe.craftSeconds

    val reduction = if (recipe.requiredSkillId == cookingId) {
      at(COOKING_TIME_REDUCTION, known.levelIn(cookingId))
    } else {
      at(MASTER_CRAFTSMAN_TIME_REDUCTION, known.levelIn(tinkererId))
    }

    // Floored rather than allowed to reach zero: a craft resolving in the tick it started would never show a
    // progress bar, and `Crafting` requires a positive duration.
    return (recipe.craftSeconds * (1f - reduction)).coerceAtLeast(MIN_CRAFT_SECONDS)
  }

  /**
   * The highest item tier this crafter can reach with [recipe], or [NO_ITEM_LEVEL_CAP] for work the design docs
   * put no ceiling on.
   *
   * Two skills have a ceiling and the rest do not, which is exactly what the docs say - Carpentry climbs from
   * 10 to "100+" and Weapon Repair from 20 to "100+". The `+` is the interesting part and is read literally: at
   * full rank the ceiling is gone rather than sitting at a hundred, because a table that ends in a plus sign is
   * saying the master has stopped being the limit.
   *
   * Inventing a ceiling for the other seven would be inventing content, so they get none - a forge with no
   * documented reach forges whatever it has a recipe for.
   */
  fun maxItemLevel(known: KnownSkills?, recipe: Recipe): Int = when (recipe.requiredSkillId) {
    carpentryId ->
      ceilingAt(CARPENTRY_ITEM_LEVEL_STEP, CARPENTRY_UNCAPPED_AT, rankIn(known, carpentryId))

    weaponRepairId ->
      ceilingAt(REPAIR_ITEM_LEVEL_STEP, REPAIR_UNCAPPED_AT, rankIn(known, weaponRepairId))

    else -> NO_ITEM_LEVEL_CAP
  }

  /**
   * A linear ceiling that disappears at [uncappedAt].
   *
   * Rank 0 gets a ceiling of 0 rather than [step], so a crafter who has not taken the skill cannot reach even
   * a tier-1 item through it - reaching this without the skill means a bug let the craft through.
   */
  private fun ceilingAt(step: Int, uncappedAt: Int, level: Int): Int = when {
    level <= 0 -> 0
    level >= uncappedAt -> NO_ITEM_LEVEL_CAP
    else -> step * level
  }

  /**
   * How many rune slots this crafter's Item Customization allows in one item, or 0 without the skill.
   *
   * A hard cap rather than a bonus: the docs' table reads "max slots", so a level 3 crafter cannot cut a
   * second slot however lucky they get.
   */
  fun maxSlots(known: KnownSkills?): Int {
    val level = known?.levelIn(itemCustomizationId) ?: 0
    if (level <= 0) return 0

    return at(CUSTOMIZATION_MAX_SLOTS, level)
  }

  /**
   * The chance a failed slot-cut destroys the item outright.
   *
   * Falls back to the *worst* row rather than to zero for a crafter with no Item Customization, because
   * reaching this without the skill means a bug let the craft through, and the safe reading of a bug is not
   * "no risk".
   */
  fun destroyChance(known: KnownSkills?): Float {
    val level = known?.levelIn(itemCustomizationId) ?: 1

    return at(CUSTOMIZATION_DESTROY_CHANCE, level.coerceAtLeast(1))
  }

  /** Master Craftsman's flat success contribution, which every workbench craft gets. */
  private fun masterCraftsmanSuccess(known: KnownSkills) =
    at(MASTER_CRAFTSMAN_SUCCESS, known.levelIn(tinkererId))

  private fun forgingBonus(known: KnownSkills) =
    at(RESEARCH_FORGING, known.levelIn(weaponryResearchId)) + at(SMITH_FORGING, known.levelIn(masterSmithId))

  private fun upgradeBonus(known: KnownSkills) =
    at(RESEARCH_UPGRADE, known.levelIn(weaponryResearchId)) + at(SMITH_UPGRADE, known.levelIn(masterSmithId))

  /**
   * The level held in a skill that may not exist in the catalogue at all.
   *
   * A null id means `skills.yml` has no such identifier, which is a content error rather than a reason to
   * fail a craft - it degrades to "does not know it", exactly as an uninvested skill does.
   */
  private fun KnownSkills.levelIn(skillId: Long?): Int = skillId?.let { levelOf(it) } ?: 0

  /** Rank 0 for a caller with no skills at all, which is every one of them until a master is resolved. */
  private fun rankIn(known: KnownSkills?, skillId: Long?): Int = known?.levelIn(skillId) ?: 0

  private fun <T> at(table: List<T>, level: Int, zero: T): T =
    if (level <= 0) zero else table[(level - 1).coerceAtMost(table.size - 1)]

  private fun at(table: List<Float>, level: Int): Float = at(table, level, 0f)

  private fun at(table: List<Int>, level: Int): Int = at(table, level, 0)

  companion object {
    /** See [craftSeconds]. */
    const val MIN_CRAFT_SECONDS = 0.1f

    /**
     * No ceiling at all - what [maxItemLevel] answers for the seven skills the docs give no table.
     *
     * [Int.MAX_VALUE] rather than a large number, so a comparison against it can never be accidentally
     * meaningful: there is no item tier that could sit above it.
     */
    const val NO_ITEM_LEVEL_CAP = Int.MAX_VALUE

    private const val CARPENTRY = "CARPENTRY"

    /**
     * The docs' "Master Craftsman". Kept as `TINKERER` because that is the identifier `skills.yml` shipped and
     * `LearnedSkill` has a foreign key to it - renaming would try to delete a referenced row.
     */
    private const val TINKERER = "TINKERER"
    private const val ITEM_CUSTOMIZATION = "ITEM_CUSTOMIZATION"
    private const val ORE_REFINEMENT = "ORE_REFINEMENT"
    private const val WEAPONRY_RESEARCH = "WEAPONRY_RESEARCH"
    private const val MASTER_SMITH = "MASTER_SMITH"
    private const val COOKING = "COOKING"
    private const val FORGE_WEAPON = "FORGE_WEAPON"
    private const val FORGE_ARMOR = "FORGE_ARMOR"
    private const val UPGRADE_EQUIPMENT = "UPGRADE_EQUIPMENT"
    private const val WEAPON_REPAIR = "WEAPON_REPAIR"

    /** Cooking Lv.1-3: +0%, +20%, +40% success and -0%, -20%, -40% time. */
    private val COOKING_SUCCESS = listOf(0.0f, 0.2f, 0.4f)
    private val COOKING_TIME_REDUCTION = listOf(0.0f, 0.2f, 0.4f)

    /** Carpentry Lv.1-10: +5% per level, reaching +50%. */
    private val CARPENTRY_SUCCESS = (1..10).map { it * 0.05f }

    /** Master Craftsman Lv.1-5: +2% per level success, -10% per level construction time. */
    private val MASTER_CRAFTSMAN_SUCCESS = (1..5).map { it * 0.02f }
    private val MASTER_CRAFTSMAN_TIME_REDUCTION = (1..5).map { it * 0.10f }

    /** Item Customization Lv.1-10: +10% per level, reaching +100%. */
    private val CUSTOMIZATION_SUCCESS = (1..10).map { it * 0.10f }

    /** Item Customization Lv.1-10: 30% destroy chance falling to 12%, two points a level. */
    private val CUSTOMIZATION_DESTROY_CHANCE = (1..10).map { 0.30f - (it - 1) * 0.02f }

    /** Item Customization: one slot up to Lv.3, three from Lv.4. */
    private val CUSTOMIZATION_MAX_SLOTS = listOf(1, 1, 1, 3, 3, 3, 3, 3, 3, 3)

    /** Ore Refinement Lv.1-3: +30% per level. */
    private val REFINEMENT_SUCCESS = listOf(0.30f, 0.60f, 0.90f)

    /** Forge Weapon / Forge Armor Lv.1-10, the docs being silent - see [successChance]. */
    private val FORGE_SUCCESS = (1..10).map { it * 0.02f }

    /** Weaponry Research Lv.1-10: +4% a level on upgrades, +1% a level on forging. */
    private val RESEARCH_UPGRADE = (1..10).map { it * 0.04f }
    private val RESEARCH_FORGING = (1..10).map { it * 0.01f }

    /** Master Smith Lv.1-5: +5% a level on both forging and upgrading. */
    private val SMITH_FORGING = (1..5).map { it * 0.05f }
    private val SMITH_UPGRADE = (1..5).map { it * 0.05f }

    /** Carpentry Lv.1-10: item tier 10 rising to "100+", so ten a level and no ceiling at full rank. */
    private const val CARPENTRY_ITEM_LEVEL_STEP = 10
    private const val CARPENTRY_UNCAPPED_AT = 10

    /** Weapon Repair Lv.1-5: item tier 20 rising to "100+", so twenty a level and no ceiling at full rank. */
    private const val REPAIR_ITEM_LEVEL_STEP = 20
    private const val REPAIR_UNCAPPED_AT = 5
  }
}
