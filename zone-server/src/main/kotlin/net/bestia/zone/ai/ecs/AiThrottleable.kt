package net.bestia.zone.ai.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks an agent whose thinking may run at a reduced cadence when nobody is near it.
 *
 * ### Opt-in, and that is the whole design
 *
 * A rule of the form "throttle anything far from a player" would be simpler and wrong. A player's bestia
 * sent off to do something at a distance has to keep acting, a boss has to behave the moment it is pulled,
 * and a scripted creature may be doing something nobody is watching yet. None of those can be distinguished
 * from scenery by position alone.
 *
 * So this is attached by exactly one caller - `AmbientSpawnerSystem`, to the baseline wilderness population
 * it creates - and everything else keeps full fidelity by simply never carrying it. Adding a second producer
 * is the thing to think twice about.
 *
 * An `object` rather than a class: there is nothing to say beyond "this one may be throttled", and
 * `Persistent` is the precedent.
 */
data object AiThrottleable : Component
