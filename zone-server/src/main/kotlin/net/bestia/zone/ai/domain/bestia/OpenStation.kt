package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.geometry.Vec3L

/** A station of its master's that waits for a free crafter, named by its structure row. */
data class OpenStation(val structureId: Long, val position: Vec3L)
