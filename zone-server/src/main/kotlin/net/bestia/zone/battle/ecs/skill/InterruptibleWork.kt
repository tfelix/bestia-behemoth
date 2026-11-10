package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.ecs.core.Component
import kotlin.reflect.KClass

/**
 * Work on the cast bar that a higher slice owns, like digging. Moving, a hit or a stun break it off the way they
 * break a craft, without battle reaching up into that slice.
 */
interface InterruptibleWork {

  /** The component whose removal ends the work and its bar. */
  val component: KClass<out Component>
}
