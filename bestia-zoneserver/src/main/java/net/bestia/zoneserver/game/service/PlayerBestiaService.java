package net.bestia.zoneserver.game.service;

import net.bestia.messages.Message;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.Zoneserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerBestiaService extends BestiaService {

	private final static Logger log = LogManager
			.getLogger(PlayerBestiaService.class);

	private final static int MAX_LEVEL = 100;

	private final PlayerBestia bestia;
	private final Account account;

	/**
	 * Ctor.
	 * 
	 * @param bestia
	 * @param account
	 *            Account object to create messages for this account.
	 */
	public PlayerBestiaService(Account account, PlayerBestia bestia,
			Zoneserver server) {
		super(bestia, server);
		if (account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}
		this.bestia = bestia;
		this.account = account;
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
		}

		// Send system message for chat.
		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Überprüft nach dem Hinzufügen von Exp ob ein Level Up stattgefunden hat
	 * und leitet danach alles weitere in die Wege. Wird von addExp gecalled.
	 * Darum muss addExp IMMER in PlayerBestia gecalled werden. Wird die EXP
	 * direkt ins Datenobjekt geschrieben werden evtl. Levelups nicht
	 * berücksichtigt.
	 * 
	 */
	private void checkLevelUp() {
		int neededExp = getNeededExp();
		/*if (bestia.getExp() < neededExp || bestia.getLevel() >= MAX_LEVEL) {
			return;
		}*/

		bestia.setExp(bestia.getExp() - neededExp);
		// Send system message for chat.

		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();
		calculateStatusValues();

		// Refill HP and Mana.
		//bestia.getStatusPoints().setCurrentHp(bestia.getStatusPoints().getMaxHp());
		//bestia.getStatusPoints().setCurrentMana(bestia.getStatusPoints().getMaxMana());
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and
	 * BaseValues. Must be called after the level of a bestia has changed.
	 */
	private void calculateStatusValues() {
		//Calculate the different stats.
		/*int atk = (bestia.getBaseValue().getAtk() * 2 + bestia.getIndividualValue().getAtk()
				+ bestia.getEffortValue().getAtk() / 4) * bestia.getLevel() / 100 + 5; 
		int def = (bestia.getBaseValue().getDef() * 2 + bestia.getIndividualValue().getDef()
				+ bestia.getEffortValue().getDef() / 4) * bestia.getLevel() / 100 + 5; 

		int spatk = (bestia.getBaseValue().getSpAtk() * 2 + bestia.getIndividualValue().getSpAtk()
				+ bestia.getEffortValue().getSpAtk() / 4) * bestia.getLevel() / 100 + 5; 
		 int spdef = (bestia.getBaseValue().getSpDef() * 2 + bestia.getIndividualValue().getSpDef()
					+ bestia.getEffortValue().getSpDef() / 4) * bestia.getLevel() / 100 + 5; 
		int spd = (bestia.getBaseValue().getSpd() * 2 + bestia.getIndividualValue().getSpd()
				+ bestia.getEffortValue().getSpd() / 4) * bestia.getLevel() / 100 + 5;  */
		// TODO HP und Mana passen nicht ins Statuspunkt konzept da sie verändelrich sind.
		// eigene klasse?
		/*
		int hp = bestia.getBaseValue().getMaxHp()
		 * $hp = floor(($this->data->get('b_hp')*2 + $this->data->get('iv_hp') +
		 * floor($this->data->get('ev_hp')/4) ) * $this->data->get('level')/100
		 * + 10 + $this->data->get('level') ); $mana =
		 * floor(($this->data->get('b_mana')*2 + $this->data->get('iv_mana') +
		 * floor($this->data->get('ev_mana')/4) ) *
		 * $this->data->get('level')/100 + 10 + $this->data->get('level') * 2);
		 */
		//StatusPoints points = bestia.getStatusPoints();
		/*points.setAtk(atk);
		points.setDef(def);
		points.setSpAtk(spatk);
		points.setSpDef(spdef);
		points.setSpd(spd);*/
		
		// HP + Mana nicht vergessen.
	}

	/**
	 * Calculates the needed experience until the next levelup.
	 * 
	 * @return Exp needed for next levelup.
	 */
	private int getNeededExp() {
		//return (int) (Math.ceil(Math.exp(bestia.getLevel() / 7)) + 10);
		return 100000;
	}

	@Override
	public void kill() {
		Location saveLoc = bestia.getSavePosition();
		moveTo(saveLoc);

		int neededExp = getNeededExp();
		// Reduce exp by 5%.
		bestia.setExp(bestia.getExp() - neededExp * 5 / 100);

		// Alle Encounter löschen
		// $encounter_dao->deleteBestiaEncounter($this->data->get('id'), false);

		// $this->msg->addMsg(array('BATTLE_BESTIA_DOWN',
		// array($this->data->get('name'))),'battle');

		// ++ Alle Statusveränderungen löschen.
		clearStatusEffects();

		//bestia.getStatusPoints().setCurrentHp(1);
		//bestia.getStatusPoints().setCurrentMana(0);
		isDead = true;
	}

	private void moveTo(Location saveLoc) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Message getDataChangedMessage() {
		// TODO Auto-generated method stub
		return null;
	}
}
