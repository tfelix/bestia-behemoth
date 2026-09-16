package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.util.EntityId

/**
 * Who is talking, as everything a topic provider could want to know about them.
 *
 * Assembled once per request and handed round, so no provider has to re-resolve an entity into a person
 * - and so the expensive half of that resolution happens once whatever a conversation ends up offering.
 *
 * [seed] is the individual. Every derived thing about them - which greeting, which variant of a
 * memory, what they happen to want to talk about today - hangs off it, so two people of one trade in
 * one town are still two people.
 */
class Speaker(
  val entityId: EntityId,
  /** `TownsfolkIdentity`, which survives the entity being torn down and rebuilt. */
  val identity: Long,
  val settlement: Int,
  val household: Int,
  val name: String,
  val occupation: Occupation,
  /**
   * The household's trade, as a `BusinessType.id`, or null for a household that keeps none.
   *
   * Beside [occupation] rather than folded into it, because the two are different questions and only
   * six occupations exist: a baker, a mason and a tanner are all the `labourer` occupation, and without
   * this they would all tell the player the same thing about what they do.
   */
  val business: String?,
  val member: Member,
  val seed: Long,
)
