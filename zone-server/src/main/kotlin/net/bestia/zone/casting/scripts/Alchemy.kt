package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** Alchemy (`skills.yml` id 21): brews potions at an Alchemy Laboratory. */
@Component
class Alchemy(losService: LineOfSightService) : CraftingSkillStrategy(losService)
