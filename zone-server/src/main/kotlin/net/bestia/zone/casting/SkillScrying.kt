package net.bestia.zone.casting

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Scry for the casting slice; the scry slice implements it. */
interface SkillScrying {

  /** Opens the caster's sight on [target]. The cast's [potency] decides how far off target the sight opens. */
  fun open(world: World, casterId: EntityId, target: ScryTarget, radiusMetres: Double, potency: Int)
}
