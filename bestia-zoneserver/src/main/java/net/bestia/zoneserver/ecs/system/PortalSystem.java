package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Portal;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.zone.shape.CollisionShape;

@Wire
public class PortalSystem extends IteratingSubscriptionSystem {
	
	private ComponentMapper<Portal> portalMapper;
	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Position> positionMapper;

	public PortalSystem() {
		super(Aspect.all(Portal.class));
		
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		subscribeActivePlayers();
	}

	@Override
	protected void process(int entityId) {
		final Portal portal = portalMapper.get(entityId);
		
		// TODO Das hier über den Quadtree lösen.
		final IntBag players = getEntities(ACTIVE_PLAYER_SUBSCRIPTION);
		
		for(int i = 0; i < players.size(); i++) {
			final CollisionShape shape = positionMapper.get(players.get(i)).position;
			
			// Do we collide? Then teleport bestia.
			if(portal.area.collide(shape)) {
				final PlayerBestiaManager pbm = playerMapper.get(players.get(i)).playerBestiaManager;
				//TODO das hier umsetzen. pbm.setLocation(portal.destination);
			}
		}
	}
}
