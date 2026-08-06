package net.bestia.zone.world

/**
 * Published once the terrain has been rebuilt after [WorldGenConfig.OnMismatch.REGENERATE] threw a world away.
 *
 * Anything holding a coordinate into the old world is now holding a coordinate into nothing. An event rather
 * than direct calls because the things that need to hear it - masters, later any persisted structures - are
 * not the world module's to know about, and because it must fire *after* the new terrain exists so a listener
 * can ask where the new spawn is.
 */
data class WorldRecreatedEvent(val world: PersistedWorld)