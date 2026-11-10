package net.bestia.zone.casting

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage

/**
 * The shape every skill in the Craftsman and Blacksmith trees has: casting it opens the crafting window with that
 * skill picked.
 *
 * A cast never builds a station. Stations come from kits, which are made in Handiwork - the window a skill opens
 * when its station is not in range - so a player who has taken Carpentry and casts it on bare ground finds the
 * workbench kit at the top of the list.
 *
 * Range comes from the catalogue through [BasicMagicSkillStrategy], so the two-tile reach the client enforces
 * while aiming and the reach the server checks are the same number.
 */
abstract class CraftingSkillStrategy(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.offerRecipes(ctx.skillId)

    // Working is not hitting: there is no number to show over anything.
    return null
  }
}
