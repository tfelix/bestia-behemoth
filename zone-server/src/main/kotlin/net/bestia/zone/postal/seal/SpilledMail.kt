package net.bestia.zone.postal.seal

import net.bestia.zone.ecs.core.Component

/**
 * Goods from a mail sack whose seal was broken. Taking them is stealing, at the honor cost of [itemLevel]. Not stored,
 * so after a restart they lie there as plain items.
 */
data class SpilledMail(val itemLevel: Int) : Component
