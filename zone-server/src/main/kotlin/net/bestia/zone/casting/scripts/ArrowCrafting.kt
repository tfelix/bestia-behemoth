package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** Arrow Crafting: a Hunter makes arrows in the wild, so like Cooking it needs no station. */
@Component
class ArrowCrafting(losService: LineOfSightService) : CraftingSkillStrategy(losService)
