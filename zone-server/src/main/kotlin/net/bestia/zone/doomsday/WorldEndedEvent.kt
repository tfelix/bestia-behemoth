package net.bestia.zone.doomsday

import net.bestia.zone.doomsday.persistence.WorldLegacy

/** The world has ended and its end is on record. What is left is to send everyone away and restart. */
data class WorldEndedEvent(val legacy: WorldLegacy)
