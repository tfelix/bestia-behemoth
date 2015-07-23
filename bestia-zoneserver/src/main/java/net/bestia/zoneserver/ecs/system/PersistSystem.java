package net.bestia.zoneserver.ecs.system;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalEntityProcessingSystem;

/**
 * System to periodically persist (player) entities which have changed into the database.
 * 
 * @author Thomas Felix
 *
 */
@Wire
public class PersistSystem extends IntervalEntityProcessingSystem {

	private static final Logger log = LogManager.getLogger(PersistSystem.class);

	@Wire
	private CommandContext cmdContext;

	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Position> pm;
	private ComponentMapper<Changable> changableMapper;

	@SuppressWarnings({ "unchecked" })
	public PersistSystem(float interval) {
		super(Aspect.all(PlayerControlled.class, Position.class, Changable.class), interval);

	}

	@Override
	protected void process(Entity e) {
		if(changableMapper.get(e).hasPersisted) {
			return;
		}
		
		synchronizeAndSaveEntity(e);
	}

	/**
	 * Persist an entity the system is interested immediately if it was removed from the world.
	 */
	@Override
	protected void removed(Entity e) {
		synchronizeAndSaveEntity(e);
	}

	private void synchronizeAndSaveEntity(Entity e) {
		final PlayerBestiaDAO dao = cmdContext.getServiceLocator().getBean(PlayerBestiaDAO.class);

		final PlayerControlled playerControlled = pcm.get(e);
		final PlayerBestia pb = playerControlled.playerBestia.getBestia();
		final Position pos = pm.get(e);

		log.trace("Persisting entity: {}", pb.toString());

		// Synchronize position with the ECS and the entity.

		// TODO hier noch den Mapwechsel bedenken.
		pb.getCurrentPosition().setX(pos.x);
		pb.getCurrentPosition().setY(pos.y);

		dao.update(pb);
		
		// Stop persisting.
		changableMapper.get(e).hasPersisted = true;
	}

}
