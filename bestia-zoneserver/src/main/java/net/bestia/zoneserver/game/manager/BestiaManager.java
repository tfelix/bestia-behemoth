package net.bestia.zoneserver.game.manager;

import javax.persistence.Transient;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.StatusEffect;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.Zoneserver;

/**
 * Simple basic service for bestias. It is abstract because
 * there must be concrete implementations for either Player or
 * NPCBestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class BestiaManager {
	
	private Bestia bestia;
	final private Zoneserver server;
	
	public BestiaManager(Bestia bestia, Zoneserver server) {
		if(bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}
		if (server == null) {
			throw new IllegalArgumentException("Zoneserver can not be null.");
		}

		this.server = server;
		this.bestia = bestia;
	}
	
	/**
	 * Handles the death of a bestia. Must be overwritten for the subclasses.
	 */
	public abstract void kill();
	
	
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
}


