package net.bestia.zoneserver.manager;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.messages.ChatMessage;
import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.AttackDAO;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.HP;
import net.bestia.zoneserver.ecs.component.Mana;
import net.bestia.zoneserver.routing.MessageProcessor;
import net.bestia.zoneserver.util.I18n;

/**
 * The PlayerBestiaManager is responsible for executing the "business logic" to
 * a {@link PlayerBestia} DTO.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestiaManager extends BestiaManager {
	private final static Logger log = LogManager.getLogger(PlayerBestiaManager.class);

	private final static int MAX_LEVEL = 40;

	private final PlayerBestia bestia;
	private final String language;
	private final MessageProcessor server;

	private final ComponentMapper<Attacks> attacksMapper;
	private final ComponentMapper<Mana> manaMapper;
	private final ComponentMapper<HP> hpMapper;

	private StatusPoints statusPoints;

	private final ServiceLocator serviceLocator;

	public PlayerBestiaManager(PlayerBestia bestia, World world, Entity entity, MessageProcessor sender,
			ServiceLocator locator) {
		super(world, entity);
		this.attacksMapper = world.getMapper(Attacks.class);
		this.manaMapper = world.getMapper(Mana.class);
		this.hpMapper = world.getMapper(HP.class);

		this.server = sender;
		this.bestia = bestia;
		this.serviceLocator = locator;

		// Shortcut to the acc. language.
		this.language = bestia.getOwner().getLanguage().toString();
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
			log.warn("Exp can not be smaller then 0. Cancelling.");
			return;
		}

		// Send system message for chat.
		sendSystemMessage(I18n.t(language, "msg.bestia gained exp", exp));

		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Returns the maximum item weight the current bestia could carry. Plase
	 * note: only the bestia master will be used to calculate the inventory max
	 * weight.
	 * 
	 * @return
	 */
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
		if (server == null) {
			// When null dont send any messages.
			return;
		}
		final ChatMessage msg = ChatMessage.getSystemMessage(bestia.getOwner(), text);
		server.processMessage(msg);

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

		// Recalculate the new status values.
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
	protected void calculateStatusValues() {

		statusPoints = new StatusPoints();

		final int atk = (bestia.getBaseValues().getAtk() * 2 + bestia.getIndividualValue().getAtk()
				+ bestia.getEffortValues().getAtk() / 4) * bestia.getLevel() / 100 + 5;

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

		statusPoints.setMaxValues(maxHp, maxMana);
		statusPoints.setCurrentHp(bestia.getCurrentHp());
		statusPoints.setCurrentMana(bestia.getCurrentMana());
		statusPoints.setAtk(atk);
		statusPoints.setDef(def);
		statusPoints.setSpAtk(spatk);
		statusPoints.setSpDef(spdef);
		statusPoints.setSpd(spd);
	}

	/**
	 * Shortcut to get the id of the wrapped bestia.
	 * 
	 * @return The id of the wrapped bestia.
	 */
	public int getPlayerBestiaId() {
		return bestia.getId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getAccountId()
	 */
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

	/**
	 * Calculates the status values depending on equipment and current bestia
	 * level.
	 * 
	 * @return The current status points.
	 */
	@Override
	public StatusPoints getStatusPoints() {
		if (statusPoints == null) {
			calculateStatusValues();
		}

		return statusPoints;
	}

	/**
	 * Enrich the location with the map on which the bestia currently resides.
	 * This information is not known for at the general level.
	 */
	@Override
	public Location getLocation() {

		final Location loc = super.getLocation();

		final String curMap = bestia.getCurrentPosition().getMapDbName();
		loc.setMapDbName(curMap);
		
		return loc;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.bestia.zoneserver.manager.PlayerBestiaManagerInterface#getLevel()
	 */
	@Override
	public int getLevel() {
		return bestia.getLevel();
	}

	/**
	 * Uses an attack in a given slot. If the slot is not set then it will do
	 * nothing. Also if the requisites for execution of an attack (mana,
	 * cooldown etc.) are not met there will also be no effect. If the slot
	 * number is invalid an {@link IllegalArgumentException} will be thrown.
	 * Slot numbering starts with 0 for the first slot.
	 * 
	 * @param slot
	 *            Number of the slot attack to be used. Starts with 0.
	 */
	public boolean useAttack(int atkId) {

		final Attacks atks = attacksMapper.get(entity);

		if (atks.hasAttack(atkId)) {
			return useAttack(atkId);
		} else {
			// Attack ID not learned. Hacking?
			return false;
		}
	}

	/**
	 * Update the underlying {@link PlayerBestia} with all the data from the ECS
	 * and return it. This should not be called very often. Retrieving and
	 * updating the player bestia triggers a few database requests and should be
	 * done only to sync back to the database.
	 * 
	 * @return
	 */
	public PlayerBestia getPlayerBestia() {

		// Update location.
		// final Vector2 pos = positionMapper.get(entity).position.getAnchor();
		// bestia.getCurrentPosition().setX(pos.x);
		// bestia.getCurrentPosition().setY(pos.y);

		// Update cur hp and mana.
		final HP hp = hpMapper.get(entity);
		final Mana mana = manaMapper.get(entity);
		bestia.setCurrentHp(hp.currentHP);
		bestia.setCurrentMana(mana.currentMana);

		// Update status values.
		final StatusPoints statusPoints = getStatusPoints();
		statusPoints.setCurrentHp(hp.currentHP);
		statusPoints.setCurrentMana(mana.currentMana);
		bestia.setStatusPoints(statusPoints);

		// Update attacks.
		final Attacks attacksComp = attacksMapper.get(entity);
		final AttackDAO atkDao = serviceLocator.getBean(AttackDAO.class);

		final Integer[] attackIds = attacksComp.getAttacks();
		int i = 0;
		for (Integer id : attackIds) {

			// Get the attack.
			final Attack attack;
			if (id == null) {
				attack = null;
			} else {
				attack = atkDao.find(id);
			}

			switch (i) {
			case 0:
				bestia.setAttack1(attack);
				break;
			case 1:
				bestia.setAttack2(attack);
				break;
			case 2:
				bestia.setAttack3(attack);
				break;
			case 3:
				bestia.setAttack4(attack);
				break;
			case 4:
				bestia.setAttack5(attack);
				break;
			}
			i++;
		}

		return bestia;
	}

	public void setAttacks(List<Integer> atkIds) {
		try {
			// TODO das ist sehr ungeschickt da hier die DB schon beschrieben
			// werden muss. Und beim persistieren noch mal.
			final PlayerBestiaService service = serviceLocator.getBean(PlayerBestiaService.class);
			service.saveAttacks(bestia.getId(), atkIds);

			final Attacks attacksComp = attacksMapper.get(entity);
			attacksComp.clear();
			attacksComp.addAll(atkIds);

		} catch (IllegalArgumentException ex) {
			// Attack can not be learned. Hacking?
			// no op.
		}
	}

	/**
	 * Returns a list of attacks of the currently wrapped bestia.
	 * 
	 * @return
	 */
	public Collection<Integer> getAttackIds() {
		final Set<Integer> atks = new HashSet<>();

		for (int i = 1; i <= 5; i++) {
			Attack atk = null;
			switch (i) {
			case 1:
				atk = bestia.getAttack1();
				break;
			case 2:
				atk = bestia.getAttack2();
				break;
			case 3:
				atk = bestia.getAttack3();
				break;
			case 4:
				atk = bestia.getAttack4();
				break;

			case 5:
				atk = bestia.getAttack5();
				break;
			}

			if (atk == null) {
				continue;
			} else {
				atks.add(atk.getId());
			}
		}
		return atks;
	}

}
