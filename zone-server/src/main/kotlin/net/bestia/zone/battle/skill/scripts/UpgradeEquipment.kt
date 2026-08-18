package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Upgrade Equipment (`skills.yml` id 44): raises an item's upgrade level by one, with a real chance of getting
 * nothing for the materials.
 *
 * The one craft in the Blacksmith block with no station, because the docs give it none - it sits in the
 * Craftsman tree rather than under the forge, and neither a range nor a target is specified for it.
 *
 * Its chance comes entirely from the two smithing passives (Weaponry Research `+4%` a level, Master Smith `+5%`
 * a level) on top of the recipe's own 30%. `CraftingService.MAX_UPGRADE_LEVEL` caps it at ten, which the docs do
 * not: unbounded is not a design when every equip script scales off the number.
 */
@Component
class UpgradeEquipment(losService: LineOfSightService) : CraftingSkillStrategy(losService, station = null)
