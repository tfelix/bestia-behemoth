package net.bestia.zone.emote.ecs

import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.movement.ecs.Path
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ends emotes: a once emote when its hold runs out, and any emote once the entity walks, casts, crafts or dies.
 *
 * Walking and a hit already end it inline where they happen - [net.bestia.zone.control.net.MoveEntityHandler] and
 * [net.bestia.zone.battle.ecs.damage.ReceivedDamageSystem] - so the player sees it on that tick. This is what catches
 * everything else: a path the AI starts for a bestia, a cast from the hotbar, a death from something that is not
 * damage.
 */
@SpringComponent
class EmoteSystem : System {
  override val phase = Phase.STATUS

  override val reads: ComponentClassSet = setOf(Path::class, Casting::class, Crafting::class, Dead::class)
  override val writes: ComponentClassSet = setOf(Animation::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Animation::class).each { id ->
      val animation = get<Animation>()
      if (!animation.isEmoting) return@each

      val interrupted = world.has(id, Path::class) || world.has(id, Casting::class) ||
        world.has(id, Crafting::class) || world.has(id, Dead::class)

      if (interrupted) {
        animation.endEmote()
        return@each
      }

      if (animation.currentAnimation.lasts) return@each

      animation.emoteSecondsLeft -= deltaTime
      if (animation.emoteSecondsLeft <= 0f) {
        animation.endEmote()
      }
    }
  }
}
