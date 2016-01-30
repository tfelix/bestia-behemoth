package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

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
		final PlayerBestiaDAO dao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);

		final PlayerBestiaManager pbm = playerMapper.get(e).playerBestiaManager;

		final net.bestia.model.domain.PlayerBestia dbPlayerBestia = dao.findOne(pbm.getPlayerBestia().getId());

		if (dbPlayerBestia == null) {
			return;
		}

		// Update its values from the ECS.
		// dbPlayerBestia.setCurrentPosition(playerBestia.getCurrentPosition());

		dbPlayerBestia.getCurrentPosition().setX(6);
		dbPlayerBestia.getCurrentPosition().setY(6);

		/*
		 * dbPlayerBestia.setCurrentHp(playerBestia.getCurrentHp());
		 * dbPlayerBestia.setCurrentMana(playerBestia.getCurrentMana());
		 * dbPlayerBestia.setLevel(playerBestia.getLevel());
		 * dbPlayerBestia.setExp(playerBestia.getExp());
		 */
		// TODO set EVs.

		dao.save(dbPlayerBestia);

		//pbService.savePlayerBestiaECS(pbm.getPlayerBestia());

		log.trace("Persisting entity: {}", pbm.toString());
	}

}
