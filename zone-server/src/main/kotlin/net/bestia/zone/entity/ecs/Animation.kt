package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * A pose the client could not work out for itself: the AI's sleep, derived every tick from the current plan step by
 * [net.bestia.zone.ai.ecs.AiActSystem], or an emote the player asked for.
 *
 * Deliberately narrow, for the reason [net.bestia.zone.ai.core.action.Posture] gives: anything an observer can
 * read off ordinary components does not belong here. Walking is the client's own, off its movement prediction.
 * An emote belongs because nothing else about a cheering master says it is cheering.
 */
data class Animation(
  private var _currentAnimation: AnimationKind = AnimationKind.IDLE
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var currentAnimation: AnimationKind
    get() = _currentAnimation
    set(value) {
      if (_currentAnimation != value) {
        _currentAnimation = value
        markDirty()
      }
    }

  /**
   * How much longer a once emote holds the pose, so a client that comes into range a moment late still sees it and
   * the same emote asked for again goes through IDLE first - a real change, which is what replays it. Zero for every
   * other pose. Server-side only.
   */
  var emoteSecondsLeft: Float = 0f

  val isEmoting: Boolean get() = _currentAnimation.isEmote

  /** Plays [kind], an emote, replacing whatever pose was showing. */
  fun startEmote(kind: AnimationKind) {
    require(kind.isEmote) { "$kind is not an emote" }
    currentAnimation = kind
    emoteSecondsLeft = kind.holdSeconds
  }

  /** Stands the entity back up when it is emoting; leaves the AI's sleep and IDLE alone. */
  fun endEmote() {
    if (isEmoting) {
      currentAnimation = AnimationKind.IDLE
      emoteSecondsLeft = 0f
    }
  }

  /**
   * @property holdSeconds for an emote played once, about as long as its clip; 0 for one that lasts until walking,
   *   casting, a hit or death ends it.
   */
  enum class AnimationKind(val isEmote: Boolean = false, val holdSeconds: Float = 0f) {
    /** Nothing overriding: the client's own walk/idle heuristic owns the pose. */
    IDLE,

    /** Lying down asleep. Only the AI sets it; a player lies down with [LIE]. */
    SLEEP,

    CHEER(isEmote = true, holdSeconds = 2f),
    JUMP(isEmote = true, holdSeconds = 1.5f),
    POINT(isEmote = true, holdSeconds = 1.5f),
    FLOURISH(isEmote = true, holdSeconds = 2f),
    SIT(isEmote = true),
    LIE(isEmote = true);

    /** What a player types after the slash to ask for this emote. */
    val command: String get() = name.lowercase()

    val lasts: Boolean get() = isEmote && holdSeconds == 0f

    companion object {
      val EMOTES: List<AnimationKind> = entries.filter { it.isEmote }
    }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return AnimationSMSG(
      entityId = entityId,
      currentAnimation = currentAnimation
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
