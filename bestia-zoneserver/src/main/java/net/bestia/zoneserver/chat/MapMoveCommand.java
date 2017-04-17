package net.bestia.zoneserver.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.entity.ComponentService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.service.PlayerEntityService;

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
	private final ComponentService componentService;

	@Autowired
	public MapMoveCommand(AccountDAO accDao, PlayerEntityService pbService, ComponentService compService) {
		super(accDao);

		this.playerBestiaService = Objects.requireNonNull(pbService);
		this.componentService = Objects.requireNonNull(compService);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/mm");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.GM;
	}

	@Override
	protected void performCommand(Account account, String text) {
		// Its okay, now execute the command.
		final Matcher match = cmdPattern.matcher(text);

		if (!match.find()) {
			LOG.debug("Wrong command usage: {}", text);
			return;
		}

		try {
			final long x = Long.parseLong(match.group(1));
			final long y = Long.parseLong(match.group(2));

			if (x < 0 || y < 0) {
				throw new IllegalArgumentException("X and Y can not be negative.");
			}

			final Entity pbe = playerBestiaService.getActivePlayerEntity(account.getId());

			Optional<PositionComponent> posComp = componentService.getComponent(pbe, PositionComponent.class);

			if(posComp.isPresent()) {
				posComp.get().setPosition(x, y);
				LOG.info("GM {} transported entity {} to x: {} y: {}.", account.getId(), pbe.getId(), x, y);
				componentService.update(posComp.get());
			}
			
		} catch (IllegalArgumentException e) {
			LOG.error("Could not parse the given coordinates.", e);
		}
	}

}
