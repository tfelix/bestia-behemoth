package net.bestia.zoneserver.entity;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.Position;
import net.bestia.model.entity.InteractionType;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.traits.Interactable;

public class PlayerEntity extends LivingEntity {

	private static final long serialVersionUID = 1L;

	public final static int MAX_LEVEL = 40;
	private final static Logger LOG = LoggerFactory.getLogger(PlayerEntity.class);

	private final long accountId;
	private final PlayerBestia playerBestia;
	private boolean isActive = false;

	public PlayerEntity(long accId, PlayerBestia playerBestia) {
		super(playerBestia.getBaseValues(), playerBestia.getIndividualValue(), playerBestia.getEffortValues(),
				playerBestia.getOrigin().getSpriteInfo());

		// Must be set rather quick because we call methods needing this
		// information.
		this.playerBestia = Objects.requireNonNull(playerBestia);
		
		this.accountId = accId;

		setVisual(playerBestia.getOrigin().getSpriteInfo());

		// Set the current HP and Mana count to the bestias values.
		getStatusPoints().setCurrentHp(playerBestia.getCurrentHp());
		getStatusPoints().setCurrentMana(playerBestia.getCurrentMana());

		// Modify the player bestia so it takes up less memory when inside the
		// cache.
		this.playerBestia.setOwner(null);
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public long getAccountId() {
		return accountId;
	}

	@Override
	public List<Attack> getAttacks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public Set<InteractionType> getPossibleInteractions(Interactable interacter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<InteractionType> getInteractions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void triggerInteraction(InteractionType type, Interactable interactor) {
		// TODO Auto-generated method stub

	}

	@Override
	public float getMaxWeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getWeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxItemCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getItemCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean addItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean dropItem(Item item, int amount) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * Returns the ID of the wrapped player bestia.
	 * 
	 * @return The id of the player bestia.
	 */
	public int getPlayerBestiaId() {
		return playerBestia.getId();
	}

	@Override
	public int getLevel() {
		return playerBestia.getLevel();
	}

	/**
	 * Restores the owner of the wrapped player bestia object. When setup in
	 * order to save memory the owner reference is removed from the player
	 * bestia. In order to get the modified player bestia it will need its owner
	 * and then return the restored object.
	 * 
	 * @param owner
	 * @return
	 */
	public PlayerBestia restorePlayerBestia(Account owner) {
		Objects.requireNonNull(owner);
		if (owner.getId() != getAccountId()) {
			throw new IllegalArgumentException("Wrong PlayerBestia object given for update.");
		}

		// Perform the update process.
		playerBestia.setOwner(owner);
		return playerBestia;
	}
	
	@Override
	public Point getPosition() {
		return playerBestia.getCurrentPosition().toPoint();
	}
	
	@Override
	public void setPosition(long x, long y) {
		playerBestia.setCurrentPosition(new Position(x, y));
	}
}
