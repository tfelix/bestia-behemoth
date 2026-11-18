package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** Transmutation (`skills.yml` id 22): refines mana essences and crystals at a Transmutation Workbench. */
@Component
class Transmutation(losService: LineOfSightService) : CraftingSkillStrategy(losService)
