package net.bestia.zoneserver.ecs.manager;

import com.artemis.annotations.Wire;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.systems.EntityProcessingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

@Wire
public class ActiveManager extends EntityProcessingSystem {
	
	private ComponentMapper<PlayerBestia> playerBestiaMapper;
	
	@Wire
	private CommandContext ctx;

	public ActiveManager() {
		super(Aspect.all(Active.class));
		
		setEnabled(false);
	}
	
	@Override
	public void inserted(Entity e) {
		if(!playerBestiaMapper.has(e)) {
			return;
		}
		
		final PlayerBestiaManager pbm = playerBestiaMapper.get(e).playerBestiaManager;
		ctx.getServer().getBestiaRegister().setActiveBestia(pbm.getAccountId(), pbm.getPlayerBestiaId());
	}
	
	@Override
	public void removed(Entity e) {
		if(!playerBestiaMapper.has(e)) {
			return;
		}
		
		final PlayerBestiaManager pbm = playerBestiaMapper.get(e).playerBestiaManager;
		ctx.getServer().getBestiaRegister().unsetActiveBestia(pbm.getAccountId(), pbm.getPlayerBestiaId());
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}

}
