package net.bestia.zoneserver.manager;

import net.bestia.messages.ChatMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.Zoneserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The PlayerBestiaManager is responsible for executing the "business logic" to a {@link PlayerBestia} DTO.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestiaManager extends BestiaManager {
	private final static Logger log = LogManager.getLogger(PlayerBestiaManager.class);

	private final static int MAX_LEVEL = 40;

	final PlayerBestia bestia;
	private final Zoneserver server;

	/**
	 * Ctor.
	 * 
	 * @param bestia
	 * @param account
	 *            Account object to create messages for this account.
	 */
	public PlayerBestiaManager(PlayerBestia bestia, Zoneserver server) {
		super(bestia.getOrigin());

		if (server == null) {
			throw new IllegalArgumentException("Zoneserver can not be null.");
		}

		this.server = server;
		this.bestia = bestia;
	}

	/**
	 * Adds a certain amount of experience to the bestia. After this it checks if a levelup has occured. Experience must
	 * be positive.
	 * 
	 * @param exp
	 *            Experience to be added.
	 */
	public void addExp(int exp) {
		if (exp < 0) {
			log.warn("Exp can not be smaller then 0. Cancelling.");
			return;
		}

		// Send system message for chat.
		sendSystemMessage(String.format("TRANS: Bestia gained %d experience.", exp));

		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Returns the maximum item weight the current bestia could carry. Plase note: only the bestia master will be used
	 * to calculate the inventory max weight.
	 * 
	 * @return
	 */
	public int getMaxItemWeight() {
		final PlayerBestia bestia = getBestia();
		return 100 + 100 * bestia.getStatusPoints().getAtk() * 3 + bestia.getLevel();
	}

	/**
	 * Sends a system message to the owner of this bestia. TODO Hier die Übersetzung kären.
	 * 
	 * @param text
	 */
	private void sendSystemMessage(String text) {
		final ChatMessage msg = ChatMessage.getSystemMessage(bestia.getOwner(), text);
		server.sendMessage(msg);

	}

	/**
	 * Checks if a level up has occured. If this is the case it will recalculate all the stats messages the user and
	 * recursively calls itself to check for multiple level ups at once.
	 * 
	 */
	private void checkLevelUp() {
		int neededExp = getNeededExp();

		if (bestia.getExp() < neededExp || bestia.getLevel() >= MAX_LEVEL) {
			return;
		}

		bestia.setExp(bestia.getExp() - neededExp);

		// Send system message for chat.
		sendSystemMessage(String.format("TRANS: Bestia reached level %d.", bestia.getLevel()));

		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();
		calculateStatusValues();

		// Refill HP and Mana.
		bestia.getStatusPoints().setCurrentHp(bestia.getStatusPoints().getMaxHp());
		bestia.getStatusPoints().setCurrentMana(bestia.getStatusPoints().getMaxMana());
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
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and BaseValues. Must be called after the level
	 * of a bestia has changed.
	 */
	@Override
	protected StatusPoints calculateStatusValues() {

		final int atk = (bestia.getBaseValues().getAtk() * 2 + bestia.getIndividualValue().getAtk() + bestia
				.getEffortValues().getAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int def = (bestia.getBaseValues().getDef() * 2 + bestia.getIndividualValue().getDef() + bestia
				.getEffortValues().getDef() / 4) * bestia.getLevel() / 100 + 5;

		final int spatk = (bestia.getBaseValues().getSpAtk() * 2 + bestia.getIndividualValue().getSpAtk() + bestia
				.getEffortValues().getSpAtk() / 4) * bestia.getLevel() / 100 + 5;

		final int spdef = (bestia.getBaseValues().getSpDef() * 2 + bestia.getIndividualValue().getSpDef() + bestia
				.getEffortValues().getSpDef() / 4) * bestia.getLevel() / 100 + 5;

		int spd = (bestia.getBaseValues().getSpd() * 2 + bestia.getIndividualValue().getSpd() + bestia
				.getEffortValues().getSpd() / 4) * bestia.getLevel() / 100 + 5;

		final int maxHp = bestia.getBaseValues().getHp() * 2 + bestia.getIndividualValue().getHp()
				+ bestia.getEffortValues().getHp() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel();
		final int maxMana = bestia.getBaseValues().getMana() * 2 + bestia.getIndividualValue().getMana()
				+ bestia.getEffortValues().getMana() / 4 * bestia.getLevel() / 100 + 10 + bestia.getLevel() * 2;

		final StatusPoints points = new StatusPoints();

		points.setMaxValues(maxHp, maxMana);
		points.setAtk(atk);
		points.setDef(def);
		points.setSpAtk(spatk);
		points.setSpDef(spdef);
		points.setSpd(spd);

		return points;
	}

	/**
	 * Calculates and sets the status values to the PlayerBestia connected with this manager. This is handy if the
	 * current status values must be send to the client.
	 */
	public void updateStatusValues() {
		bestia.setStatusPoints(getStatusPoints());
	}

	public PlayerBestia getBestia() {
		return bestia;
	}

	/**
	 * Shortcut to get the id of the wrapped bestia.
	 * 
	 * @return The id of the wrapped bestia.
	 */
	public int getPlayerBestiaId() {
		return bestia.getId();
	}

	@Override
	public String toString() {
		return String.format("PlayerBestiaManager[bestia: %s]", bestia);
	}
}
