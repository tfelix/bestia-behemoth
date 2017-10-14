package net.bestia.zoneserver.actor.map;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.map.MapTilesetMessage;
import net.bestia.messages.map.MapTilesetRequestMessage;
import net.bestia.model.domain.TilesetData;
import net.bestia.model.map.Tileset;
import net.bestia.model.map.TilesetService;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * The user queries the name/data of an {@link TilesetData}. He only sends the
 * GID of the tile and we must find the appropriate tileset.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class TilesetRequestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static String NAME = "tileset";

	private final TilesetService tilesetService;
	private final ActorRef sendClient;

	@Autowired
	public TilesetRequestActor(TilesetService tilesetService) {

		this.tilesetService = Objects.requireNonNull(tilesetService);
		sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(MapTilesetRequestMessage.class, this::onMapTilesetRequest)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// Register for chat commands.
		final RedirectMessage redirMsg = RedirectMessage.get(MapTilesetRequestMessage.class);
		getContext().parent().tell(redirMsg, getSelf());
	}

	private void onMapTilesetRequest(MapTilesetRequestMessage msg) {

		final Optional<Tileset> ts = tilesetService.findTileset(msg.getTileId());

		if (!ts.isPresent()) {
			LOG.warning("Tileset containing gid {} not found.", msg.getTileId());
			return;
		}

		final MapTilesetMessage response = new MapTilesetMessage(
				msg.getAccountId(),
				ts.get().getSimpleTileset());

		sendClient.tell(response, getSelf());
	}

}
