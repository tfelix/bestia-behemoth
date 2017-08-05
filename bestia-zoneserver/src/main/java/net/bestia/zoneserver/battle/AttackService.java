package net.bestia.zoneserver.battle;

import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;

/**
 * Provides all access to let entities (player bestias) learn attacks or check
 * attack related things.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class AttackService {

	
	public boolean knowsAttack(Entity entity, int attackId) {
		// TODO Fixme
		return true;
	}
}
