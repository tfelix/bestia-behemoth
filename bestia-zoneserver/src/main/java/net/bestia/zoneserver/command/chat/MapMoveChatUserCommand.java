package net.bestia.zoneserver.command.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.MapMoveMessage;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;

public class MapMoveChatUserCommand implements ChatUserCommand {
	
	private static final Logger LOG = LogManager.getLogger(MapMoveChatUserCommand.class);
	
	private final Pattern mapPattern= Pattern.compile("(\\D+) (\\d+) (\\d+)");
	private final Pattern cordsPattern = Pattern.compile("(\\d+) (\\d+)");

	@Override
	public void execute(ChatMessage message, CommandContext ctx) {
		
		final int activeBestia = ctx.getServer().getBestiaRegister().getActiveBestia(message.getAccountId());
		
		final String text = message.getText().substring(3).trim();
		final Matcher m1 = mapPattern.matcher(text);
		final Matcher m2 = cordsPattern.matcher(text);
		
		Location target = null;
		if(m1.find()) {
			final String loc = m1.group(1);
			final int x = Integer.parseInt(m1.group(2));
			final int y = Integer.parseInt(m1.group(3));
			target = new Location(loc, x, y);
		} else if(m2.find()) {
			final int x = Integer.parseInt(m2.group(1));
			final int y = Integer.parseInt(m2.group(2));		
			target = new Location("", x, y);
		} else {
			// Command not understood.
			LOG.info("Chatinput not understood: {}", message.toString());
			final AccountDAO accDao = ctx.getServiceLocator().getBean(AccountDAO.class);
			final Account acc = accDao.find(message.getAccountId());
			final ChatMessage reply = ChatMessage.getSystemMessage(acc, "etc.unknown_command");
			ctx.getServer().sendMessage(reply);
			return;
		}
		
		final MapMoveMessage mmMessage = new MapMoveMessage(message.getAccountId(), activeBestia);
		mmMessage.setTarget(target);	
		ctx.getServer().getMessageRouter().processMessage(mmMessage);
	}

	@Override
	public String getChatToken() {
		return "/mm";
	}

	@Override
	public UserLevel getNeededUserLevel() {
		return UserLevel.GM;
	}

}
