package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;

public class PlayerBestiaEntity extends LivingEntity {

	private static final long serialVersionUID = 1L;
	private final static Logger LOG = LoggerFactory.getLogger(PlayerBestiaEntity.class);
	private final static int MAX_LEVEL = 40;

	private final long accountId;
	private final PlayerBestia playerBestia;

	public PlayerBestiaEntity(PlayerBestia pb) {
		super(pb.getBaseValues(), pb.getIndividualValue(), pb.getEffortValues(), pb.getOrigin().getDatabaseName());

		this.playerBestia = Objects.requireNonNull(pb);
		this.accountId = pb.getOwner().getId();

		// Set the current HP and Mana count to the bestias values.
		getStatusPoints().setCurrentHp(playerBestia.getCurrentHp());
		getStatusPoints().setCurrentMana(playerBestia.getCurrentMana());

		// Modify the player bestia so it takes up less memory when inside the
		// cache.
		this.playerBestia.setOwner(null);
	}

	/**
	 * Adds a certain amount of experience to the bestia. After this it checks
	 * if a levelup has occured. Experience must be positive.
	 * 
	 * @param exp
	 *            Experience to be added.
	 */
	public void addExp(int exp) {
		if (exp < 0) {
			LOG.warn("Exp can not be smaller then 0. Cancelling.");
			return;
		}

		// Send system message for chat.
		// sendSystemMessage(I18n.t(language, "MSG.bestia_gained_exp",
		// bestia.getName(), exp));

		playerBestia.setExp(playerBestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Checks if a level up has occured. If this is the case it will recalculate
	 * all the stats messages the user and recursively calls itself to check for
	 * multiple level ups at once.
	 * 
	 */
	private void checkLevelUp() {
		final int neededExp = getNeededExp();

		if (playerBestia.getExp() < neededExp || playerBestia.getLevel() >= MAX_LEVEL) {
			return;
		}

		playerBestia.setExp(playerBestia.getExp() - neededExp);
		playerBestia.setLevel(playerBestia.getLevel() + 1);

		// Send system message for chat.
		// sendSystemMessage(I18n.t(language, "msg.bestia_reached_level",
		// bestia.getName(), bestia.getLevel()));

		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();

		// Recalculate the new status values.
		calculateStatusPoints();

		// Refill HP and Mana.
		final StatusPoints statusPoints = getOriginalStatusPoints();
		statusPoints.setCurrentHp(statusPoints.getCurrentHp());
		statusPoints.setCurrentMana(statusPoints.getCurrentMana());

		calculateRegenerationRates();
	}

	/**
	 * Uses an attack in a given slot. If the slot is not set then it will do
	 * nothing. Also if the requisites for execution of an attack (mana,
	 * cooldown etc.).
	 * 
	 */
	public boolean useAttack(Attack atk) {

		if (atk.getId() == Attack.BASIC_MELEE_ATTACK_ID) {
			return true;
		}

		if (!playerBestia.getAttacks().contains(atk)) {
			return false;
		}

		return getStatusPoints().subtractMana(atk.getManaCost());
	}

	/**
	 * Kills a player bestia.
	 */
	public void kill() {
		super.kill();
		LOG.debug("Player bestia {} was killed.", getPlayerBestiaId());
	}

	public Collection<Attack> getAttacks() {
		return playerBestia.getAttacks();
	}

	public long getAccountId() {
		return accountId;
	}

	@Override
	public String toString() {
		return String.format("PlayerBestiaEntity[%s]", playerBestia.toString());
	}

	/**
	 * Shortcut to get the id of the wrapped bestia.
	 * 
	 * @return The id of the wrapped bestia.
	 */
	public int getPlayerBestiaId() {
		return playerBestia.getId();
	}

	/**
	 * Calculates the needed experience until the next levelup.
	 * 
	 * @return Exp needed for next levelup.
	 */
	private int getNeededExp() {
		return (int) (Math.ceil(Math.exp(playerBestia.getLevel()) / 3) + 15);
	}

	@Override
	public int getLevel() {
		return playerBestia.getLevel();
	}

	/**
	 * Updates the given player bestia model with the latest information from
	 * the entity. The updated model can then be used to be send to the database
	 * of to the client. The given {@link PlayerBestia} must match with the
	 * wrapped player bestia. Otherwise an {@link IllegalArgumentException} is
	 * thrown.
	 * 
	 * @param pb
	 */
	public void updateModel(PlayerBestia pb) {
		Objects.requireNonNull(pb);
		if(pb.getId() != getPlayerBestiaId()) {
			throw new IllegalArgumentException("Wrong PlayerBestia object given for update.");
		}
		
		// Perform the update process.
		StatusPoints sp = getStatusPoints();
		pb.setCurrentHp(sp.getCurrentHp());
		pb.setCurrentMana(sp.getCurrentMana());
	}
}
