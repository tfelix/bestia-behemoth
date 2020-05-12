package de.tfelix.bestia.worldgen

interface WorkloadFactory {
  fun buildWorkload(): List<Workload>
}