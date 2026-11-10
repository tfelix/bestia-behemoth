package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.geometry.Vec3L

/** The chest a bestia's job fills, named by its structure row; its prop entity changes when its column reloads. */
data class JobChest(val structureId: Long, val position: Vec3L)
