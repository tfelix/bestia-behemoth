package net.bestia.zoneserver.actor.map;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.map.MapChunkMessage;
import net.bestia.messages.map.MapChunkRequestMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.map.MapChunk;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.map.MapService;

/**
 * This actor generates a data message containing all the data/map chunks needed
 * for the client to render a certain piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class MapRequestChunkActor extends BestiaRoutingActor {

	public final static String NAME = "mapChunk";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService pbService;
	private final EntityService entityService;
	private final MapService mapService;

	@Autowired
	public MapRequestChunkActor(
			MapService mapService,
			PlayerEntityService pbService,
			EntityService entityService) {
		super(Arrays.asList(MapChunkRequestMessage.class));

		this.pbService = Objects.requireNonNull(pbService);
		this.mapService = Objects.requireNonNull(mapService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final MapChunkRequestMessage req = (MapChunkRequestMessage) msg;

		// Find the currently active bestia for this account.
		final Entity pbe = pbService.getActivePlayerEntity(req.getAccountId());

		final Optional<PositionComponent> pos = entityService.getComponent(pbe, PositionComponent.class);

		if(!pos.isPresent()) {
			return;
		}
		
		final Point point = pos.get().getPosition();

		// Verify if the player is able to request the given chunk ids.
		if (!MapService.areChunksInClientRange(point, req.getChunks())) {
			LOG.warning("Player requested invalid chunks. Message: {}", req.toString());
			return;
		}

		final List<MapChunk> chunks = mapService.getChunks(req.getChunks());

		final MapChunkMessage response = new MapChunkMessage(req.getAccountId(), chunks);
		AkkaSender.sendClient(getContext(), response);
	}
}
