package net.bestia.zoneserver.ecs.system;

import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

/**
 * System to periodically persist (player) entities which have changed into the
 * database.
 * 
 * @author Thomas Felix
 *
 */
@Wire
public class PersistSystem extends IntervalEntityProcessingSystem {

	private static final Logger log = LogManager.getLogger(PersistSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;

	public PersistSystem(float interval) {
		super(Aspect.all(PlayerBestia.class), interval);

	}

	@Override
	protected void process(Entity e) {
		synchronizeAndSaveEntity(e);
	}

	/**
	 * Persist an entity the system is interested immediately if it was removed
	 * from the world.
	 */
	@Override
	public void removed(Entity e) {
		log.trace("PlayerBestia Entity id: {} removed. Persisting.", e.getId());
		synchronizeAndSaveEntity(e);
	}

	private void synchronizeAndSaveEntity(Entity e) {
		final PlayerBestiaService pbService = ctx.getServiceLocator().getBean(PlayerBestiaService.class);
		final PlayerBestiaEntityProxy pbm = playerMapper.get(e).playerBestia;

		pbService.savePlayerBestiaECS(pbm.getPlayerBestia());

		log.trace("Persisting entity: {}", pbm.toString());
	}

}
