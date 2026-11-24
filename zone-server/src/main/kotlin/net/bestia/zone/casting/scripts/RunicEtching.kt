package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.CraftingSkillStrategy
import org.springframework.stereotype.Component

/** Runic Etching (`skills.yml` id 18): binds one of the caster's Bestias into a cut gem, at a Carving Table. */
@Component
class RunicEtching(losService: LineOfSightService) : CraftingSkillStrategy(losService)
