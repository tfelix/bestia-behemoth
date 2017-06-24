package net.bestia.zoneserver.chat;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.component.StatusComponent;

/**
 * The set command is a very powerful admin and debugging command. It can set
 * arbitrary values to the current selected bestia or any other entity.
 * 
 * Usage: <code>
 *	/set [ENITITY_ID] hp 1
 * </code>
 * 
 * This will set the hp to 1 for the given entity ID or the currently selected
 * entity if no id was given.
 * 
 * <code>
 *  /set [ENTITY_ID] position[Component].position 10 10
 * </code>
 * 
 * This will try to set the position component of the given entity to the given
 * coordiantes. In order to perform this settings a lot of guessing of method
 * names and reflection magic is involved. This command is therefore quite
 * DANGEROUS. Use with care.
 * 
 * NOTE: Currently only HP and Mana is supported.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class SetCommand extends BaseChatCommand {

	private static final Logger LOG = LoggerFactory.getLogger(MapMoveCommand.class);
	private static final Pattern cmdPattern = Pattern.compile("/set (\\d+ )?([\\w\\.]+) (.*)");
	
	private final EntityService entityService;
	private final PlayerEntityService playerBestiaService;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public SetCommand(AccountDAO accDao, 
			ZoneAkkaApi akkaApi,
			EntityService entityService, 
			PlayerEntityService playerBestiaService) {
		super(accDao);
		
		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}
	
	@Override
	public boolean isCommand(String text) {
		return text.startsWith("/set");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.ADMIN;
	}
	
	@Override
	protected void executeCommand(Account account, String text) {
		LOG.info("Chatcommand: /set triggered by account {}.", account.getId());
		
		final Matcher match = cmdPattern.matcher(text);
		
		if (!match.find()) {
			LOG.debug("Wrong command usage: {}", text);
			return;
		}
		
		try {
			final String component = match.group(2);
			final long arg = Long.parseLong(match.group(3));
			
			final Entity pbe = playerBestiaService.getActivePlayerEntity(account.getId());
			final StatusComponent status = entityService.getComponent(pbe, StatusComponent.class).get();

			if(component.toLowerCase().equals("hp")) {
				status.getValues().setCurrentHealth((int) arg);
			} else if(component.toLowerCase().equals("mana")) {
				status.getValues().setCurrentMana((int) arg);
			}
			
			entityService.saveComponent(status);
			
		} catch (Exception e) {
			final ChatMessage replyMsg = ChatMessage.getSystemMessage(account.getId(), "Error: " + e.getMessage());
			akkaApi.sendToClient(replyMsg);
			LOG.error("Could not parse the given coordinates.", e);
		}
	}
}
