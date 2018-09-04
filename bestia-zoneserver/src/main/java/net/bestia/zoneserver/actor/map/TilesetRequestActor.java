package net.bestia.zoneserver.actor.map;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.map.MapTilesetMessage;
import net.bestia.messages.map.MapTilesetRequestMessage;
import net.bestia.model.domain.TilesetData;
import net.bestia.model.map.Tileset;
import net.bestia.model.map.TilesetService;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor;
import net.bestia.zoneserver.actor.client.SendToClientActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * The user queries the name/data of an {@link TilesetData}. He only sends the
 * GID of the tile and we must find the appropriate tileset.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class TilesetRequestActor extends BaseClientMessageRouteActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static String NAME = "tileset";

	private final TilesetService tilesetService;
	private final ActorRef sendClient;

	@Autowired
	public TilesetRequestActor(TilesetService tilesetService) {

		this.tilesetService = Objects.requireNonNull(tilesetService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendToClientActor.class);
		getRedirectConfig().match(MapTilesetRequestMessage.class, this::onMapTilesetRequest);
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