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
	private ComponentService component;
	
	public ComponentService getComponent() {
		return component;
	}
	
	@Autowired
	public void setComponent(ComponentService component) {
		this.component = component;
	}
	
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
