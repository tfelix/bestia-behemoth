package bestia.zoneserver.chat;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bestia.entity.Entity;
import bestia.entity.factory.MobFactory;
import bestia.messages.MessageApi;
import bestia.model.domain.Account;
import bestia.model.domain.Account.UserLevel;

/**
 * Spawns a mob for a given mob database name.
 * 
 * @author Thomas Felix
 *
 */
public class MobSpawnModule extends SubCommandModule {

	private static final Logger LOG = LoggerFactory.getLogger(MobSpawnModule.class);

	private static final Pattern CMD_PATTERN = Pattern.compile("mob (\\w+) (\\d+) (\\d+)");
	private final MobFactory mobFactory;

	public MobSpawnModule(MessageApi akkaApi, MobFactory mobFactory) {
		super(akkaApi);

		this.mobFactory = Objects.requireNonNull(mobFactory);
	}

	@Override
	public boolean isCommand(String text) {
		return text.startsWith("mob ");
	}

	@Override
	public UserLevel requiredUserLevel() {
		return UserLevel.SUPER_GM;
	}

	@Override
	protected Pattern getMatcherPattern() {
		return CMD_PATTERN;
	}

	@Override
	protected String getHelpText() {
		return "Usage: /spawn mob <MOB_DB_NAME> <POS_X> <POS_Y>";
	}

	@Override
	protected void executeCheckedCommand(Account account, String text, Matcher matcher) {
		LOG.info("Chatcommand: /spawn mob triggered by account {}.", account.getId());

		final String mobName = matcher.group(1);
		final long x = Long.parseLong(matcher.group(2));
		final long y = Long.parseLong(matcher.group(3));

		final Entity e = mobFactory.build(mobName, x, y);

		if (e == null) {
			sendSystemMessage(account.getId(), String.format("Mob %s could not be spawned.", mobName));
		}
	}
}
