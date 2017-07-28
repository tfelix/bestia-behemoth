package net.bestia.zoneserver.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * Moves the player to the given map coordinates if he has GM permissions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class MapMoveCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);
	private final static Pattern cmdPattern = Pattern.compile("/mm (\\d+) (\\d+)");

	private final PlayerEntityService playerBestiaService;
	private final EntityService entityService;
	private final MapParameterDAO mapParamDao;

	@Autowired
	public MapMoveCommand(
			ZoneAkkaApi akkaApi,
			PlayerEntityService pbService,
			EntityService entityService,
			MapParameterDAO mapParamDao) {
		super(akkaApi);

		this.playerBestiaService = Objects.requireNonNull(pbService);
		this.entityService = Objects.requireNonNull(entityService);
		this.mapParamDao = Objects.requireNonNull(mapParamDao);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/mm ");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.GM;
	}

	@Override
	public void executeCommand(Account account, String text) {
		LOG.info("Chatcommand: /mm triggered by account {}.", account.getId());

		// Its okay, now execute the command.
		final Matcher match = cmdPattern.matcher(text);

		if (!match.find()) {
			LOG.debug("Wrong command usage: {}", text);
			sendSystemMessage(account.getId(), getHelpText());
			return;
		}

		try {
			final long x = Long.parseLong(match.group(1));
			final long y = Long.parseLong(match.group(2));

			if (x < 0 || y < 0) {
				sendSystemMessage(account.getId(), "Illegal coordiantes. Must be positive and inside the map.");
				throw new IllegalArgumentException("X and Y can not be negative.");
			}

			final MapParameter params = mapParamDao.findFirstByOrderByIdDesc();

			if (params == null) {
				throw new IllegalStateException("Seems there is no map. Can not determine map size.");
			}

			if (x > params.getWorldSize().getWidth() || y > params.getWorldSize().getHeight()) {
				sendSystemMessage(account.getId(), "Illegal coordiantes. Must be positive and inside the map.");
				return;
			}

			final Entity pbe = playerBestiaService.getActivePlayerEntity(account.getId());

			final Optional<PositionComponent> posComp = entityService.getComponent(pbe, PositionComponent.class);

			if (posComp.isPresent()) {
				posComp.get().setPosition(x, y);
				LOG.info("GM {} transported entity {} to x: {} y: {}.", account.getId(), pbe.getId(), x, y);
				entityService.updateComponent(posComp.get());
			} else {
				sendSystemMessage(account.getId(), "Selected entity has no position component present.");
				return;
			}

		} catch (IllegalArgumentException e) {
			LOG.error("Could not parse the given coordinates.", e);
		}
	}

	@Override
	protected String getHelpText() {
		return "Usage: /mm <X> <Y>";
	}

}
