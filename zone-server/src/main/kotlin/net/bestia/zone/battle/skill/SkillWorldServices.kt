package net.bestia.zone.battle.skill

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.effects.AreaEffectSpawner
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.prop.PlayerStructureService
import org.springframework.stereotype.Component

/**
 * The services a [SkillWorld] delegates to, collected once so a per-cast [SkillWorld] costs one reference
 * rather than seven.
 *
 * A service belongs here rather than in a script's own constructor when its methods need a world to work
 * against - that is the whole reason it cannot be reached from a script directly. A service that needs no
 * world (a config, a calculator) a script may inject itself.
 *
 * Everything here is a singleton with no dependency back on the ECS `World` bean, which is what keeps it
 * out of the boot cycle described on [SkillWorld].
 */
@Component
class SkillWorldServices(
  val aoi: EntityAOIService,
  val structures: PlayerStructureService,
  val areaEffectSpawner: AreaEffectSpawner,
  val statusEffects: StatusEffectService,
  val messages: OutMessageProcessor,
  val crafting: CraftingService,
  val survey: SurveyService,
)
