package net.bestia.zone.ai.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks an agent that may drop to [AiDetail.BACKGROUND] when nobody can see it.
 *
 * ### Opt-in, and that is the whole design
 *
 * A rule of the form "slow anything nobody sees to a crawl" would be simpler and wrong. A player's bestia
 * sent off to do something at a distance has to keep acting, a boss has to behave the moment it is pulled,
 * and a scripted creature may be doing something nobody is watching yet. None of those can be distinguished
 * from scenery by position alone, so they never carry this and never drop below [AiDetail.REDUCED].
 *
 * Attached only to the population the server fills the world with - `AmbientSpawnerSystem`'s wilderness and
 * `TownsfolkEntitySpawner`'s townspeople. Adding another producer is the thing to think twice about.
 *
 * An `object` rather than a class: there is nothing to say beyond "this one may be slowed", and
 * `Persistent` is the precedent.
 */
data object AiThrottleable : Component
