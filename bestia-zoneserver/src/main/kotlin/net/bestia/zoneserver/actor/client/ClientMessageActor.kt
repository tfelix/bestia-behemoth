package net.bestia.zoneserver.actor.client

import mu.KotlinLogging
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.chat.ChatActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class ClientMessageActor : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
  }

  override fun preStart() {

    // === Connection ===
    // SpringExtension.actorOf(context, ClientConnectionManagerActor::class.java)
    // SpringExtension.actorOf(context, LatencyManagerActor::class.java)

    // === Bestias ===
    // SpringExtension.actorOf(getContext(), ActivateBestiaActor.class);
    // SpringExtension.actorOf(context, BestiaInfoActor::class.java)

    // === Map ===
    // SpringExtension.actorOf(context, MapRequestChunkActor::class.java)
    // SpringExtension.actorOf(context, TilesetRequestActor::class.java)

    // === Entities ===
    // SpringExtension.actorOf(getContext(), EntityInteractionRequestActor.class);
    // SpringExtension.actorOf(getContext(), PlayerMoveRequestActor.class);

    // === Attacking ===
    // SpringExtension.actorOf(getContext(), AttackUseActor.class);

    // === UI/Client ===
    SpringExtension.actorOf(context, ClientVarActor::class.java)

    // === Chat ===
    SpringExtension.actorOf(context, ChatActor::class.java)
  }

  companion object {
    const val NAME = "clientMessages"
  }
}
