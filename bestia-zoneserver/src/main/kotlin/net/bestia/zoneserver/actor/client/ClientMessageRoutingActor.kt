package net.bestia.zoneserver.actor.client

import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.battle.AttackUseActor
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor
import net.bestia.zoneserver.actor.chat.ChatActor
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor

/**
 * Actor which handles the normal client incoming messages.
 */
@Actor
class ClientMessageRoutingActor : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {}

  override fun preStart() {
    // === Bestias ===
    SpringExtension.actorOf(context, ActivateBestiaActor::class.java)

    // === Map ===
    // SpringExtension.actorOf(context, MapRequestChunkActor::class.java)
    // SpringExtension.actorOf(context, TilesetRequestActor::class.java)

    // === Entities ===
    SpringExtension.actorOf(context, EntityInteractionRequestActor::class.java)

    // === Attacking ===
    SpringExtension.actorOf(context, AttackUseActor::class.java)

    // === UI/Client ===
    SpringExtension.actorOf(context, ClientVarActor::class.java)

    // === Chat ===
    SpringExtension.actorOf(context, ChatActor::class.java)
  }

  companion object {
    const val NAME = "clientMessages"
  }
}
