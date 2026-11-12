package net.bestia.zone.movement.ecs

import net.bestia.zone.ecs.core.Component

/** The entity walks on ground nobody made: no road, no paving, no built floor. Kept only for a Trailblazer. */
data object OffRoad : Component
