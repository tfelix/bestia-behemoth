package de.tfelix.bestia.worldgen

data class WorkloadStatus(
    val identifier: String,
    val progress: Float,
    val hasFinished: Boolean
)