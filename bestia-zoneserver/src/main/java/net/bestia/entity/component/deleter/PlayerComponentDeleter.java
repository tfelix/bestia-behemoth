package net.bestia.entity.component.deleter;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;

/**
 * If a player bestia component is deleted the referenced bestia gets its ID
 * removed so it is clear that no active entity exists anymore representing this
 * bestia.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class PlayerComponentDeleter extends ComponentDeleter<PlayerComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(PositionComponentDeleter.class);
	private final PlayerBestiaDAO playerDao;

	@Autowired
	public PlayerComponentDeleter(EntityService entityService, PlayerBestiaDAO playerDao) {
		super(entityService, PlayerComponent.class);

		this.playerDao = Objects.requireNonNull(playerDao);
	}

	@Override
	protected void doFreeComponent(PlayerComponent component) {

		LOG.debug("Recycling PlayerComponent: {}.", component);

		final long pbid = component.getPlayerBestiaId();
		final PlayerBestia playerBestia = playerDao.findOne(pbid);

		if (playerBestia == null) {
			LOG.warn("Could not find player bestia with id {}.", pbid);
			return;
		}

		playerBestia.setEntityId(0L);
		playerDao.save(playerBestia);
	}

}
