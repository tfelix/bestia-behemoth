package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Forge Armor (`skills.yml` id 14): beats ingots and a blueprint into a cuirass, at a forge.
 *
 * Uses a forge and never builds one, unlike [ForgeWeapon] - the docs give placement to the weapon half of the
 * pair and say nothing about it here, and inventing a second way to build a forge would make the distinction
 * meaningless.
 *
 * Shares Forge Weapon's chance sources exactly: both smithing passives plus a step per level of this skill.
 */
@Component
class ForgeArmor(losService: LineOfSightService) :
  CraftingSkillStrategy(losService, StaticEntityKind.FORGE)
