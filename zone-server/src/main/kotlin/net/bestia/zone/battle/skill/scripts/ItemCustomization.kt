package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Item Customization (`skills.yml` id 11): cuts rune slots into a finished item, at a workbench.
 *
 * Uses a workbench and never builds one - that is Carpentry's job, and this skill sits behind it in the tree
 * anyway. Its level decides three things, all in `MasterCraftBonusService`: `+10%` success a level, a destroy
 * chance on failure falling from 30% to 12%, and the hard cap of one slot up to level 3 and three from level 4.
 *
 * The cap is a cap rather than a bonus, so a level 3 crafter cannot cut a second slot however lucky they get.
 * Nothing can fill a slot yet: runes are Artificer work (`RUNIC_ETCHING`) and that sub-tree is not built.
 */
@Component
class ItemCustomization(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.WORKBENCH)
