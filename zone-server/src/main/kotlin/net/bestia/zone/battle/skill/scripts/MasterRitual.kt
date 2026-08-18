package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.CraftingSkillStrategy
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * Master Ritual (`skills.yml` id 8): converts twenty-five void essence, five mana dust and three clay into a
 * Seal of Mastery, at the ratio the design docs give.
 *
 * One recipe and no station: a ritual is performed on bare ground. The recipe cannot fail, which is the one
 * place `baseSuccessChance: 1.0` is used in the catalogue - a ritual that ate twenty-five void essence and gave
 * nothing back would be read as a bug rather than as risk.
 *
 * What a Seal of Mastery *opens* is not built. The item exists, the ritual makes it, and there is nothing yet to
 * spend it on; that is the honest state of it rather than a hidden no-op.
 */
@Component
class MasterRitual(losService: LineOfSightService) : CraftingSkillStrategy(losService, station = null)
