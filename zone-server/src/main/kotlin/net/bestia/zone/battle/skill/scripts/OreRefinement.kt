package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Ore Refinement (`skills.yml` id 12): smelts iron ore into ingots, and is what puts a furnace up.
 *
 * Reconciled with the docs in the catalogue pass: it used to be `PASSIVE` here, and the docs make it an active
 * 10-second cast that also unlocks placing a furnace at level 1. So it builds one on empty ground and opens the
 * refining list at a furnace, exactly as Carpentry does with a workbench.
 *
 * Its level is worth `+30%` success a level over three levels, which is the steepest table in either tree and
 * the reason refining is worth investing in rather than gambling at.
 */
@Component
class OreRefinement(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.FURNACE, placesStation = true)
