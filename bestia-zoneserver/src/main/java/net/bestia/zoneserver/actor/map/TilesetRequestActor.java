package net.bestia.zoneserver.actor.map;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.map.MapTilesetMessage;
import net.bestia.messages.map.MapTilesetRequestMessage;
import net.bestia.model.dao.TilesetDAO;
import net.bestia.model.domain.Tileset;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * The user queries the name/data of an {@link Tileset}. He only sends the GID
 * of the tile and we must find the appropriate tileset.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class TilesetRequestActor extends BestiaRoutingActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static String NAME = "tileset";
	
	private final TilesetDAO tilesetDao;

	@Autowired
	public TilesetRequestActor(TilesetDAO tilesetDao) {
		super(Arrays.asList(MapTilesetRequestMessage.class));
		
		this.tilesetDao = Objects.requireNonNull(tilesetDao);
	}
	
	@Override
	protected void handleMessage(Object msg) {
		final MapTilesetRequestMessage mtmsg = (MapTilesetRequestMessage) msg;
		
		final Tileset ts = tilesetDao.findByGid(mtmsg.getTileId());
		
		if(ts == null) {
			LOG.warning("Tileset containing gid {} not found.", mtmsg.getTileId());
			return;
		}
		
		final MapTilesetMessage response = new MapTilesetMessage(mtmsg, ts);
		sendClient(response);
	}

}
