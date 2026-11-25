package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** Minting (`skills.yml` id 51): strikes gold bars into coin at a Mint. */
@Component
class Minting(losService: LineOfSightService) : CraftingSkillStrategy(losService)
