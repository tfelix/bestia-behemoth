package net.bestia.zoneserver.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class simply combines multiple entity services for easy access to them.
 * 
 * @author Thomas Felix
 *
 */
@Component
public final class EntityServiceContext {
	
	private EntityService entity;
	private InteractionService interact;
	private PlayerEntityService player;
	private MovementService move;
	
	public EntityService getEntity() {
		return entity;
	}
	
	@Autowired
	public void setEntity(EntityService entity) {
		this.entity = entity;
	}
	
	public InteractionService getInteract() {
		return interact;
	}
	
	public MovementService getMove() {
		return move;
	}
	
	@Autowired
	public void setMove(MovementService move) {
		this.move = move;
	}
	
	@Autowired
	public void setInteract(InteractionService interact) {
		this.interact = interact;
	}
	
	public PlayerEntityService getPlayer() {
		return player;
	}
	
	@Autowired
	public void setPlayer(PlayerEntityService player) {
		this.player = player;
	}

}
