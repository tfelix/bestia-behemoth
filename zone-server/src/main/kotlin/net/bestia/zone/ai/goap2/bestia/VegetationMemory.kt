package net.bestia.zone.ai.goap2.bestia

import net.bestia.zone.geometry.Vec3L

/** A remembered foraging spot: "there was food here as of [discoveredAtMs]." */
data class VegetationMemory(val position: Vec3L, val discoveredAtMs: Long)
