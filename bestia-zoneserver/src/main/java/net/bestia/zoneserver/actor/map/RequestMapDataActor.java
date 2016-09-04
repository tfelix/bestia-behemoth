package net.bestia.zoneserver.actor.map;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.Message;
import net.bestia.messages.map.MapDataMessage;
import net.bestia.messages.map.RequestMapDataMessage;
import net.bestia.messages.map.TilesetDataMassage;
import net.bestia.model.zone.Point;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendResponseActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.service.MapService;
import net.bestia.zoneserver.zone.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.shape.Rect;

/**
 * This actor generates a data message containing all the data needed for the
 * client to render a certain piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class RequestMapDataActor extends BestiaRoutingActor {

	/**
	 * How many tiles are transmitted from the position of the player in each
	 * direction to the client.
	 */
	private static final int VIEW_RANGE = 32;

	private final MapService mapService;
	private final CacheManager<Long, Integer> activeBestiaCache;
	private final CacheManager<Integer, PlayerBestiaEntity> playerBestiaCache;
	private ActorRef responseActor;

	@Autowired
	public RequestMapDataActor(
			MapService mapService,
			@Qualifier(CacheConfiguration.ACTIVE_BESTIA_CACHE) CacheManager<Long, Integer> activeBestiaCache,
			@Qualifier(CacheConfiguration.PLAYER_BESTIA_CACHE) CacheManager<Integer, PlayerBestiaEntity> playerBestiaCache) {

		this.activeBestiaCache = Objects.requireNonNull(activeBestiaCache);
		this.mapService = Objects.requireNonNull(mapService);
		this.playerBestiaCache = Objects.requireNonNull(playerBestiaCache);

	}

	@Override
	public void preStart() throws Exception {
		super.preStart();

		this.responseActor = createAndRegisterActor(SendResponseActor.class, "responder");
	}

	@Override
	protected List<Class<? extends Message>> getHandledMessages() {
		return Arrays.asList(RequestMapDataMessage.class);
	}

	@Override
	protected void handleMessage(Message msg) {

		final RequestMapDataMessage req = (RequestMapDataMessage) msg;
		final long accId = req.getAccountId();

		// Find the currently active bestia for this account.
		final Integer activeBestia = activeBestiaCache.get(accId);
		final PlayerBestiaEntity pbe = playerBestiaCache.get(activeBestia);

		final Point pos = pbe.getPosition();
		final Rect area = new Rect(
				pos.getX() - VIEW_RANGE,
				pos.getY() - VIEW_RANGE,
				pos.getX() + VIEW_RANGE,
				pos.getY() + VIEW_RANGE);

		// Retrieve all the map information in the view port of this.
		final Map map = mapService.getMap(area);

		// Assemble the answer message and find the actor with the ref.
		final MapDataMessage mapData = null;
		final TilesetDataMassage tileData = null;
		responseActor.tell(msg, getSelf());
		responseActor.tell(tileData, getSelf());
	}

}
