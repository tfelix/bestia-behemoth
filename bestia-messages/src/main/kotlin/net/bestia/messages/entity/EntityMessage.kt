package net.bestia.messages.entity

/**
 * Class can be send to a client and contains an entity id.
 *
 * @author Thomas Felix
 */
interface EntityMessage {
  val entityId: Long
}
