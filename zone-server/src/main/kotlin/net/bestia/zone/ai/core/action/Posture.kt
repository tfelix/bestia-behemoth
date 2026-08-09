package net.bestia.zone.ai.core.action

/**
 * What an [Action] looks like from the outside while it is being carried out.
 *
 * Small and coarse on purpose. This is not an animation name — the ECS `Animation` component owns that
 * vocabulary and the client owns the clips — it is the one thing the *planner's* vocabulary can say that a
 * renderer could not otherwise work out: whether the creature is up and about or lying down. Everything else
 * an observer needs is already visible in ordinary components (moving means it has a `Path`).
 *
 * It hangs off the action rather than being set by the behaviour tree because a plan step can end without its
 * tree being ticked again — a sleeping creature that gets bitten has its plan replaced outright — so anything
 * a leaf switched on when it started would have nobody to switch it off. Reading the posture of whatever step
 * is current cannot get stuck that way.
 */
enum class Posture {

  /** Up and about: standing, walking, fighting, grazing. */
  ACTIVE,

  /** Lying down asleep. */
  SLEEPING,
}
