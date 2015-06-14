package net.bestia.model.service;

import javax.persistence.Transient;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;

/**
 * Simple basic service for bestias. It is abstract because
 * there must be concrete implementations for either Player or
 * NPCBestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BestiaService {
	
	
	private Bestia bestia;
	protected boolean isDead;
	
	public BestiaService(Bestia bestia, MessageSender server) {

		if(bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}
		this.bestia = bestia;
	}
	
	/**
	 * Handles the death of a bestia. Must be overwritten for the subclasses.
	 */
	public abstract void kill();
	
	/**
	 * Returns a boolean value if the bestia was dead or not. This can be used to
	 * stop an ongoing battle if one of the bestias were defeated. (Once they are killed
	 * their hp is set to 1 thus it can not be checked with the HP alone if a bestia
	 * was killed by enemy damage.)
	 * 
	 * @return Bool flag if the bestia was dead or not.
	 */
	public boolean isDead() {
		return isDead;
	}
	
	/**
	 * Return the CHANGED status value of the bestia. This includes changes from equipment and
	 * status changes. In contrast to NPCBestia this implementation respects equipment.
	 * @return
	 */
	public StatusPoints getStatusData() {
		return null;
	}
	
	public void addStatusEffect(StatusEffect effect) {
		return;
	}
	
	public void removeStatusEffect(StatusEffect effect) {
		return;
	}
	
	public void removeStatusEffect(int statusEffectId) {
		return;
	}
	
	/**
	 * Deletes all status effects.
	 */
	public void clearStatusEffects() {
		return;
	}
	
	public StatusPoints getOriginalStatus() {
		//return bestia.getStatusPoints();
		return null;
	}
}


