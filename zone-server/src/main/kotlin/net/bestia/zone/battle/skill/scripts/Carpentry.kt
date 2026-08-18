package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Carpentry (`skills.yml` id 9): the root of the Craftsman tree, and the only way a workbench gets built.
 *
 * Aimed at ground with no workbench near it, this puts one up; aimed at a workbench, it opens what can be made
 * there. That double duty is deliberate and is what makes the tree discoverable from one skill: the docs give
 * Carpentry a workbench as its target without ever saying where the first workbench comes from.
 *
 * Its own level is worth `+5%` success a level up to `+50%`, which is what the docs' table says.
 *
 * The docs also give it a max item level (10 rising to 100+). Items have no level - `upgradeLevel` is a
 * different thing, counting how often one has been improved - so that half of the table has no substrate here
 * and is left unimplemented rather than mapped onto something it does not mean.
 */
@Component
class Carpentry(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.WORKBENCH, placesStation = true)
