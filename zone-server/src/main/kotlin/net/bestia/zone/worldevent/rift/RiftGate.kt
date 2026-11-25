package net.bestia.zone.worldevent.rift

import net.bestia.zone.ecs.core.Component

/** The gate an open rift shows: which rift it belongs to. */
data class RiftGate(val riftId: Long) : Component
