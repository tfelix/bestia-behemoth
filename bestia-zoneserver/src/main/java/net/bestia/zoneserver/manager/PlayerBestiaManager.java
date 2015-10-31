package net.bestia.zoneserver.manager;

import net.bestia.messages.ChatMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.util.I18n;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The PlayerBestiaManager is responsible for executing the "business logic" to
 * a {@link PlayerBestia} DTO.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestiaManager extends BestiaManager implements PlayerBestiaManagerInterface {
	private final static Logger log = LogManager.getLogger(PlayerBestiaManager.class);

	private final static int MAX_LEVEL = 40;
	private final PlayerBestia bestia;
	private final String language;
	private final Zoneserver server;

	/**
	 * Ctor.
	 * 
	 * @param bestia
	 * @param account
	 *            Account object to create messages for this account.
	 */
	public PlayerBestiaManager(PlayerBestia bestia, Zoneserver server) {
		if (server == null) {
			throw new IllegalArgumentException("Zoneserver can not be null.");
		}

		this.server = server;
		this.bestia = bestia;

		// Shortcut to the acc. language.
		this.language = bestia.getOwner().getLanguage().toString();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#addExp(int)
	 */
	@Override
	public void addExp(int exp) {
		if (exp < 0) {
			log.warn("Exp can not be smaller then 0. Cancelling.");
			return;
		}

		// Send system message for chat.
		sendSystemMessage(I18n.t(language, "msg.bestia gained exp", exp));

		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getMaxItemWeight()
	 */
	@Override
	public int getMaxItemWeight() {
		StatusPoints sp = getStatusPoints();
		return 100 + 100 * sp.getAtk() * 3 + bestia.getLevel();
	}

	/**
	 * Sends a system message to the owner of this bestia.
	 * 
	 * @param text
	 */
	private void sendSystemMessage(String text) {
		final ChatMessage msg = ChatMessage.getSystemMessage(bestia.getOwner(),
				text);
		server.sendMessage(msg);

	}

	/**
	 * Checks if a level up has occured. If this is the case it will recalculate
	 * all the stats messages the user and recursively calls itself to check for
	 * multiple level ups at once.
	 * 
	 */
	private void checkLevelUp() {
		int neededExp = getNeededExp();

		if (bestia.getExp() < neededExp || bestia.getLevel() >= MAX_LEVEL) {
			return;
		}

		bestia.setExp(bestia.getExp() - neededExp);

		// Send system message for chat.
		sendSystemMessage(I18n.t(language, "msg.bestia reached level", bestia.getLevel()));

		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();
		calculateStatusValues();

		// Refill HP and Mana.
		bestia.getStatusPoints().setCurrentHp(
				bestia.getStatusPoints().getMaxHp());
		bestia.getStatusPoints().setCurrentMana(
				bestia.getStatusPoints().getMaxMana());
	}

	/**
	 * Calculates the needed experience until the next levelup.
	 * 
	 * @return Exp needed for next levelup.
	 */
	private int getNeededExp() {
		return (int) (Math.ceil(Math.exp(bestia.getLevel() / 7)) + 10);
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	protected StatusPoints calculateStatusValues() {

		final int atk = (bestia.getBaseValues().getAtk() * 2
				+ bestia.getIndividualValue().getAtk() + bestia
						.getEffortValues().getAtk() / 4)
				* bestia.getLevel() / 100 + 5;

		final int def = (bestia.getBaseValues().getDef() * 2
				+ bestia.getIndividualValue().getDef() + bestia
						.getEffortValues().getDef() / 4)
				* bestia.getLevel() / 100 + 5;

		final int spatk = (bestia.getBaseValues().getSpAtk() * 2
				+ bestia.getIndividualValue().getSpAtk() + bestia
						.getEffortValues().getSpAtk() / 4)
				* bestia.getLevel()
				/ 100
				+ 5;

		final int spdef = (bestia.getBaseValues().getSpDef() * 2
				+ bestia.getIndividualValue().getSpDef() + bestia
						.getEffortValues().getSpDef() / 4)
				* bestia.getLevel()
				/ 100
				+ 5;

		int spd = (bestia.getBaseValues().getSpd() * 2
				+ bestia.getIndividualValue().getSpd() + bestia
						.getEffortValues().getSpd() / 4)
				* bestia.getLevel() / 100 + 5;

		final int maxHp = bestia.getBaseValues().getHp() * 2
				+ bestia.getIndividualValue().getHp()
				+ bestia.getEffortValues().getHp() / 4 * bestia.getLevel()
						/ 100
				+ 10 + bestia.getLevel();
		final int maxMana = bestia.getBaseValues().getMana() * 2
				+ bestia.getIndividualValue().getMana()
				+ bestia.getEffortValues().getMana() / 4 * bestia.getLevel()
						/ 100
				+ 10 + bestia.getLevel() * 2;

		final StatusPoints points = new StatusPoints();

		points.setMaxValues(maxHp, maxMana);
		points.setAtk(atk);
		points.setDef(def);
		points.setSpAtk(spatk);
		points.setSpDef(spdef);
		points.setSpd(spd);

		return points;
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#updateStatusValues()
	 */
	@Override
	public void updateStatusValues() {
		bestia.setStatusPoints(getStatusPoints());
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getPlayerBestia()
	 */
	@Override
	public PlayerBestia getPlayerBestia() {
		return bestia;
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getPlayerBestiaId()
	 */
	@Override
	public int getPlayerBestiaId() {
		return bestia.getId();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getAccountId()
	 */
	@Override
	public long getAccountId() {
		return bestia.getOwner().getId();
	}

	@Override
	public String toString() {
		return String.format("PlayerBestiaManager[%s]", bestia.toString());
	}

	@Override
	public int hashCode() {
		return bestia.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;

		PlayerBestiaManager other = (PlayerBestiaManager) obj;

		if (bestia == null) {
			if (other.bestia != null)
				return false;
		} else if (!bestia.equals(other.bestia))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getStatusPoints()
	 */
	@Override
	public StatusPoints getStatusPoints() {
		final int atk = (bestia.getBaseValues().getAtk() * 2 + 5 + bestia
				.getEffortValues().getAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int def = (bestia.getBaseValues().getDef() * 2 + 5 + bestia
				.getEffortValues().getDef() / 4) * bestia.getLevel() / 100 + 5;

		final int spatk = (bestia.getBaseValues().getSpAtk() * 2 + 5 + bestia
				.getEffortValues().getSpAtk() / 4)
				* bestia.getLevel()
				/ 100
				+ 5;

		final int spdef = (bestia.getBaseValues().getSpDef() * 2 + 5 + bestia
				.getEffortValues().getSpDef() / 4)
				* bestia.getLevel()
				/ 100
				+ 5;

		int spd = (bestia.getBaseValues().getSpd() * 2 + 5 + bestia
				.getEffortValues().getSpd() / 4) * bestia.getLevel() / 100 + 5;

		final int maxHp = bestia.getBaseValues().getHp() * 2 + 5
				+ bestia.getEffortValues().getHp() / 4 * bestia.getLevel()
						/ 100
				+ 10 + bestia.getLevel();
		final int maxMana = bestia.getBaseValues().getMana() * 2 + 5
				+ bestia.getEffortValues().getMana() / 4 * bestia.getLevel()
						/ 100
				+ 10 + bestia.getLevel() * 2;

		final StatusPoints statusPoints = new StatusPoints();

		statusPoints.setMaxValues(maxHp, maxMana);
		statusPoints.setAtk(atk);
		statusPoints.setDef(def);
		statusPoints.setSpAtk(spatk);
		statusPoints.setSpDef(spdef);
		statusPoints.setSpd(spd);

		return statusPoints;
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getLocation()
	 */
	@Override
	public Location getLocation() {
		return bestia.getCurrentPosition();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getLevel()
	 */
	@Override
	public int getLevel() {
		return bestia.getLevel();
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#setAttack(int, net.bestia.model.domain.Attack)
	 */
	@Override
	public void setAttack(int slot, Attack atk) {
		if (slot <= MAX_ATK_SLOTS || slot < 0) {
			throw new IllegalArgumentException(String.format(
					"Slot must be between 0 and %d.", MAX_ATK_SLOTS));
		}

		switch (slot) {
		case 0:
			bestia.setAttack1(atk);
			break;
		case 1:
			bestia.setAttack2(atk);
			break;
		case 2:
			bestia.setAttack3(atk);
			break;
		case 3:
			bestia.setAttack4(atk);
			break;
		case 4:
			bestia.setAttack5(atk);
			break;
		default:
			throw new IllegalStateException("This attack slot does not exist: "
					+ slot);
		}
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#useAttackInSlot(int)
	 */
	@Override
	public boolean useAttackInSlot(int slot) {
		final Attack atk = getAttackInSlot(slot);
		return useAttack(atk);
	}

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getAttackInSlot(int)
	 */
	@Override
	public Attack getAttackInSlot(int slot) {
		if (slot < 0 || slot >= MAX_ATK_SLOTS) {
			throw new IllegalArgumentException(
					"Slot number must be between 0 and " + (MAX_ATK_SLOTS - 1));
		}

		final Attack atk;
		switch (slot) {
		case 0:
			atk = bestia.getAttack1();
			break;
		case 1:
			atk = bestia.getAttack2();
			break;
		case 2:
			atk = bestia.getAttack3();
			break;
		case 3:
			atk = bestia.getAttack4();
			break;
		case 4:
			atk = bestia.getAttack5();
			break;
		default:
			// Should not happen.
			atk = null;
			break;
		}

		return atk;
	}
}
