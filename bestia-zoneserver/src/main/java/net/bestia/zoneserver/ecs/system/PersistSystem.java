package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.ChangedData;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.event.PersistEvent;
import net.mostlyoriginal.api.event.common.Subscribe;

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

	@SuppressWarnings({ "unchecked"})
	public PersistSystem(float interval) {
		super(Aspect.all(PlayerControlled.class, Position.class, ChangedData.class), interval);

	}

	@Override
	protected void process(Entity e) {
		synchronizeAndSaveEntity(e);
		e.edit().remove(ChangedData.class);
	}

	/**
	 * Is triggered is a persistence action is NOW required.
	 */
	@Subscribe
	public void onPersistNowEvent(PersistEvent event) {
		synchronizeAndSaveEntity(event.entity);
		event.entity.edit().remove(ChangedData.class);
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
	}

}
