package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Weapon Repair (`skills.yml` id 15): puts a worn item back to full durability, at a forge.
 *
 * The first thing in the game to read `ItemInstance.durability`, and the reason it exists. Refuses an item that
 * does not wear at all and one that is already whole - both would take the materials for nothing.
 *
 * The docs give this skill a max item level (20 rising to 100+) and no success table. Items have no level, so
 * that table has no substrate and the recipe's own 80% stands unmodified; `MasterCraftBonusService` deliberately
 * adds nothing here rather than inventing a curve.
 */
@Component
class WeaponRepair(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.FORGE)
