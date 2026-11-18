package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** GemCutting (`skills.yml` id 36): cuts rough gems at a Gem Cut Station. */
@Component
class GemCutting(losService: LineOfSightService) : CraftingSkillStrategy(losService)
