package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Forge Weapon (`skills.yml` id 13): beats ingots and a blueprint into a weapon, and is what puts a forge up.
 *
 * The docs give forge placement to this skill and not to [ForgeArmor], so this is the one that builds one -
 * which also means a smith who took only armour smithing works at somebody else's forge, or at the one they
 * built with the weapon half of the tree.
 *
 * Forging draws on both smithing passives, Weaponry Research (`+1%` a level) and Master Smith (`+5%` a level),
 * plus a small step per level of this skill itself - the docs give it no success table of its own, and ten
 * points spent to buy nothing but the right to place a forge would be a strange investment.
 */
@Component
class ForgeWeapon(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.FORGE, placesStation = true)
