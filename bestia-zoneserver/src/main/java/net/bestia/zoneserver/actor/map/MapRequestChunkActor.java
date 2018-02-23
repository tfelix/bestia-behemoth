package net.bestia.zoneserver.actor.map;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.map.MapChunkMessage;
import net.bestia.messages.map.MapChunkRequestMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.map.MapChunk;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.map.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This actor generates a data message containing all the data/map chunks needed
 * for the client to render a certain piece of a map.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MapRequestChunkActor extends AbstractActor {

	public final static String NAME = "mapChunk";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService pbService;
	private final EntityService entityService;
	private final MapService mapService;
	private final ActorRef sendClient;

	@Autowired
	public MapRequestChunkActor(
			MapService mapService,
			PlayerEntityService pbService,
			EntityService entityService) {

		this.pbService = Objects.requireNonNull(pbService);
		this.mapService = Objects.requireNonNull(mapService);
		this.entityService = Objects.requireNonNull(entityService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(MapChunkRequestMessage.class, this::onMapChunkRequest)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// Register for chat commands.
		final RedirectMessage redirMsg = RedirectMessage.get(MapChunkRequestMessage.class);
		getContext().parent().tell(redirMsg, getSelf());
	}

	private void onMapChunkRequest(MapChunkRequestMessage msg) {

		// Find the currently active bestia for this account.
		final Entity pbe = pbService.getActivePlayerEntity(msg.getAccountId());

		final Optional<PositionComponent> pos = entityService.getComponent(pbe, PositionComponent.class);

		if (!pos.isPresent()) {
			return;
		}

		final Point point = pos.get().getPosition();

		// Verify if the player is able to request the given chunk ids.
		if (!MapService.areChunksInClientRange(point, msg.getChunks())) {
			LOG.warning("Player requested invalid chunks. Message: {}", msg);
			return;
		}

		final List<MapChunk> chunks = mapService.getChunks(msg.getChunks());

		final MapChunkMessage response = new MapChunkMessage(msg.getAccountId(), chunks);
		sendClient.tell(response, getSelf());
	}
}
