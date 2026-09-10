package net.bestia.zone.ai.domain

import net.bestia.zone.ai.core.goal.Goal

/**
 * What a domain is called, and what a profile is allowed to name in it.
 *
 * Deliberately free of collaborators, which is the whole reason it is not part of [AiDomainRuntime].
 * `AiProfileRegistry` validates a profile the moment it is parsed, before there is a world to act in, so
 * whatever it checks against has to exist without a navigation service or a battle pipeline behind it.
 */
interface AiDomainCatalogue {

  /** What a profile's `domain:` names. */
  val id: String

  val actionIds: Set<String>

  val goalsByName: Map<String, Goal>
}
