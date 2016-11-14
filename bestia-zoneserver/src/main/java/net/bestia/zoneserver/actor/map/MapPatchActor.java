package net.bestia.zoneserver.actor.map;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.map.MapChunkRequestMessage;
import net.bestia.model.map.Map;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Rect;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.entity.MasterBestiaEntity;
import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.service.MapService;

/**
 * This actor generates a data message containing all the data needed for the
 * client to render a certain piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class MapPatchActor extends BestiaRoutingActor {

	public final static String NAME = "mapPatch";

	/**
	 * How many tiles are transmitted from the position of the player in each
	 * direction to the client.
	 */
	private static final int VIEW_RANGE = 32;

	private final MapService mapService;
	private final CacheManager<Long, Integer> activeBestiaCache;
	private final CacheManager<Integer, MasterBestiaEntity> playerBestiaCache;

	@Autowired
	public MapPatchActor(
			MapService mapService,
			@Qualifier(CacheConfiguration.ACTIVE_BESTIA_CACHE) CacheManager<Long, Integer> activeBestiaCache,
			@Qualifier(CacheConfiguration.PLAYER_BESTIA_CACHE) CacheManager<Integer, MasterBestiaEntity> playerBestiaCache) {
		super(Arrays.asList(MapChunkRequestMessage.class));

		this.activeBestiaCache = Objects.requireNonNull(activeBestiaCache);
		this.mapService = Objects.requireNonNull(mapService);
		this.playerBestiaCache = Objects.requireNonNull(playerBestiaCache);
	}

	@Override
	protected void handleMessage(Object msg) {

		final MapChunkRequestMessage req = (MapChunkRequestMessage) msg;
		final long accId = req.getAccountId();

		// Find the currently active bestia for this account.
		final Integer activeBestia = activeBestiaCache.get(accId);
		final MasterBestiaEntity pbe = playerBestiaCache.get(activeBestia);

		final Point pos = pbe.getPosition();
		final Rect area = new Rect(
				pos.getX() - VIEW_RANGE,
				pos.getY() - VIEW_RANGE,
				pos.getX() + VIEW_RANGE,
				pos.getY() + VIEW_RANGE);

		// Retrieve all the map information in the view port of this.
		final Map map = mapService.getMap(area);

		// Assemble the answer message and find the actor with the ref.
		// final MapDataMessage mapData = new MapDataMessage(map.getSize(), );
		// responseActor.tell(mapData, getSelf());
	}
}
