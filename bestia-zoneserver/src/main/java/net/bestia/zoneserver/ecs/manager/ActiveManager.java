package net.bestia.zoneserver.ecs.manager;

import com.artemis.annotations.Wire;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.systems.EntityProcessingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * This system keeps track of the active bestia. If a new player bestia is added
 * the thread safe central {@link AccountRegistry} will get notified by the
 * change.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
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
		if (!playerBestiaMapper.has(e)) {
			return;
		}

		final PlayerBestiaEntityProxy pbm = playerBestiaMapper.get(e).playerBestiaManager;
		ctx.getAccountRegistry().setActiveBestia(pbm.getAccountId(), pbm.getPlayerBestiaId());
	}

	@Override
	public void removed(Entity e) {
		if (!playerBestiaMapper.has(e)) {
			return;
		}

		final PlayerBestiaEntityProxy pbm = playerBestiaMapper.get(e).playerBestiaManager;
		ctx.getAccountRegistry().unsetActiveBestia(pbm.getAccountId(), pbm.getPlayerBestiaId());
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}

}
