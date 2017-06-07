package net.bestia.zoneserver.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.zoneserver.battle.BattleService;
import net.bestia.zoneserver.script.ScriptService;

/**
 * This class simply combines multiple entity services for easy access to them.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EntityServiceContext {
	
	private EntityService entity;
	private InteractionService interact;
	private PlayerEntityService player;
	private MovingEntityService move;
	private BattleService battleService;
	private ScriptService scriptService;
	private StatusService statusService;
	
	public EntityService getEntity() {
		return entity;
	}
	
	public StatusService getStatusService() {
		return statusService;
	}
	
	@Autowired
	public void setStatusService(StatusService statusService) {
		this.statusService = statusService;
	}
	
	@Autowired
	public void setScriptService(ScriptService scriptService) {
		this.scriptService = scriptService;
	}
	
	public ScriptService getScriptService() {
		return scriptService;
	}
	
	@Autowired
	public void setEntity(EntityService entity) {
		this.entity = entity;
	}
	
	public InteractionService getInteract() {
		return interact;
	}
	
	public MovingEntityService getMove() {
		return move;
	}
	
	@Autowired
	public void setMove(MovingEntityService move) {
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
	
	@Autowired
	public void setBattleService(BattleService battleService) {
		this.battleService = battleService;
	}
	
	public BattleService getBattleService() {
		return battleService;
	}

}
