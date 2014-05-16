package net.bestia.core.game.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.core.game.model.Account;
import net.bestia.core.game.model.PlayerBestia;

public class PlayerBestiaService extends BestiaService {
	
	private final static Logger log = LogManager.getLogger(PlayerBestiaService.class);
	
	private final static int MAX_LEVEL = 100;
	
	private final PlayerBestia bestia;
	private final Account account;

	/**
	 * Ctor.
	 * 
	 * @param bestia
	 */
	public PlayerBestiaService(Account account, PlayerBestia bestia) {
		super(bestia);
		if(account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}
		this.bestia = bestia;
		this.account = account;
	}
	
	/**
	 * Adds a certain amount of experience to the bestia. After this it checks
	 * if a levelup has occured. Experience must be positive.
	 * 
	 * @param exp Experience to be added.
	 */
	public void addExp(int exp) {
		if(exp < 0) {
			log.warn("Exp can not be smaller then 0. Cancelling.");
		}
		
		// Send system message for chat.
		bestia.setExp(bestia.getExp() + exp);
		checkLevelUp();
	}

	/**
	 * Überprüft nach dem Hinzufügen von Exp ob ein Level Up stattgefunden hat und leitet
	 * danach alles weitere in die Wege. Wird von addExp gecalled. Darum muss addExp IMMER in PlayerBestia
	 * gecalled werden. Wird die EXP direkt ins Datenobjekt geschrieben werden evtl. Levelups nicht berücksichtigt.
	 *
	 */
	private void checkLevelUp() {
		int neededExp = getNeededExp();
		if(bestia.getExp() < neededExp || bestia.getLevel() >= MAX_LEVEL) {
			return;
		}
		
		bestia.setExp(bestia.getExp() - neededExp);
		// Send system message for chat.
		
		// Check recursivly for other level ups until all level ups are done.
		checkLevelUp();
		calculateStatusValues();
		
		// Refill HP and Mana.
		bestia.setCurrentHp(bestia.getMaxHp());
		bestia.setCurrentMana(bestia.getMaxMana());
	}

	/**
	 * Recalculates the status values of a bestia. It uses the EVs, IVs and BaseValues.
	 * Must be called after the level of a bestia has changed.
	 */
	private void calculateStatusValues() {
		/*
		 //Calculate the different stats.
		$atk = floor(($this->data->get('b_atk')*2 + $this->data->get('iv_atk') + floor($this->data->get('ev_atk')/4) ) * $this->data->get('level')/100 + 5 );
		$ver = floor(($this->data->get('b_ver')*2 + $this->data->get('iv_ver') + floor($this->data->get('ev_ver')/4) ) * $this->data->get('level')/100 + 5 );
		$spatk = floor(($this->data->get('b_spatk')*2 + $this->data->get('iv_spatk') + floor($this->data->get('iv_spatk')/4) ) * $this->data->get('level')/100 + 5 );
		$spver = floor(($this->data->get('b_spver')*2 + $this->data->get('iv_spver') + floor($this->data->get('iv_spver')/4) ) * $this->data->get('level')/100 + 5 );
		$spd = floor(($this->data->get('b_spd')*2 + $this->data->get('iv_spd') + floor($this->data->get('iv_spd')/4) ) * $this->data->get('level')/100 + 5 );
		
		$hp = floor(($this->data->get('b_hp')*2 + $this->data->get('iv_hp') + floor($this->data->get('ev_hp')/4) ) * $this->data->get('level')/100 + 10 + $this->data->get('level') );
		$mana = floor(($this->data->get('b_mana')*2 + $this->data->get('iv_mana') + floor($this->data->get('ev_mana')/4) ) * $this->data->get('level')/100 + 10 + $this->data->get('level') * 2);

        $this->status_points->setValue('atk',$atk);
        $this->status_points->setValue('ver',$ver);
        $this->status_points->setValue('sp_atk',$spatk);
        $this->status_points->setValue('sp_ver',$spver);
        $this->status_points->setValue('spd',$spd);

        $this->status_points->setValue('max_hp',$hp);
        $this->status_points->setValue('max_mana',$mana);
		 */
	}

	/**
	 * Calculates the needed experience until the next levelup.
	 * @return Exp needed for next levelup.
	 */
	private int getNeededExp() {
		return (int) (Math.ceil(Math.exp(bestia.getLevel() / 7)) + 10);
	}

	@Override
	public void kill() {
		/*
		 $save_loc = $this->data->getSaveLocation();
		$this->moveTo($save_loc->getPosX(), $save_loc->getPosY(), $save_loc->getMapDbName());
        
		$exp_tnl = $this->getNeededExp();
		$this->data->setExp($this->data->get('exp') - floor($exp_tnl * 5 / 100));
        
		//$reg = Registry::getInstance();		
		//Nachschauen ob ein AI Objekt schon vorliegt oder noch nicht. Je nachdem erzeugen.
		//$dao_fac = $reg->get('dao_factory');
		$encounter_dao = $this->dao_fac->getDAO('encounter');
		$status_dao = $this->dao_fac->getDAO('status');
		
		//Alle Encounter löschen
        $encounter_dao->deleteBestiaEncounter($this->data->get('id'), false);
		
        $this->msg->addMsg(array('BATTLE_BESTIA_DOWN', array($this->data->get('name'))),'battle');

        //++ Alle Statusveränderungen löschen.
        $status_dao->deleteStatusEffects($this->data->get('id'));

        $this->status_points->setValue('cur_hp', 1);
        $this->status_points->setValue('cur_mana', 0);
        $this->b_dead = 1;
		 */
	}

}
