package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Cooking (`skills.yml` id 3): the Novice tree's one craft, and the only one that needs no station.
 *
 * The docs put it at a camp fire, and there is no camp-fire prop to stand next to - so it is unstationed rather
 * than gated on something that does not exist. Its level buys what the docs say it buys: `-20%` cooking time and
 * `+20%` success a level, both applied by `MasterCraftBonusService`, which deliberately does not let the
 * reduction stack with Master Craftsman's since the two sit in different trees.
 *
 * The stamina cost the docs give (5 to 11 by level) has no field anywhere in the catalogue and is left out.
 */
@Component
class Cooking(losService: LineOfSightService) : CraftingSkillStrategy(losService, station = null)
