package net.bestia.core.game.service;

import net.bestia.core.game.model.Bestia;
import net.bestia.core.game.model.StatusPoint;

public abstract class BestiaService {
	private Bestia bestia;
	private boolean isDead;
	
	public BestiaService(Bestia bestia) {
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
	public StatusPoint getStatusData() {
		return null;
	}
	
	public StatusPoint getOriginalStatus() {
		return bestia.getStatusPoints();
	}
}


