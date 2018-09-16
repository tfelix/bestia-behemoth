package net.bestia.entity.component.interceptor;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * After a {@link PlayerComponent} is added to an entity the referenced player
 * bestia needs to be found and updated with the player bestia id so existing
 * entities can later be re-identified if the user re-connects to the system.
 *
 * @author Thomas Felix
 */
@Component
public class PlayerComponentInterceptor extends BaseComponentInterceptor<PlayerComponent> {

  private static final Logger LOG = LoggerFactory.getLogger(PlayerComponentInterceptor.class);

  private final PlayerBestiaDAO playerBestiaDao;

  @Autowired
  public PlayerComponentInterceptor(PlayerBestiaDAO playerBestiaDao) {
    super(PlayerComponent.class);

    this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
  }

  @Override
  protected void onUpdateAction(EntityService entityService, Entity entity, PlayerComponent comp) {
    // no op.
  }

  @Override
  protected void onCreateAction(EntityService entityService, Entity entity, PlayerComponent comp) {
    LOG.debug("intercept onCreate: PlayerComponent.");

    final long pbid = comp.getPlayerBestiaId();
    final PlayerBestia playerBestia = playerBestiaDao.findById(pbid).get();

    if (playerBestia == null) {
      LOG.warn("Could not find player bestia with id: {}.", pbid);
      return;
    }

    playerBestia.setEntityId(entity.getId());
    playerBestiaDao.save(playerBestia);
  }

  @Override
  protected void onDeleteAction(EntityService entityService, Entity entity, PlayerComponent comp) {
    LOG.debug("intercept onDelete: PlayerComponent.");

    final long pbid = comp.getPlayerBestiaId();
    final PlayerBestia playerBestia = playerBestiaDao.findById(pbid).get();

    if (playerBestia == null) {
      LOG.warn("Could not find player bestia with id {}.", pbid);
      return;
    }

    playerBestia.setEntityId(0L);
    playerBestiaDao.save(playerBestia);
  }
}
