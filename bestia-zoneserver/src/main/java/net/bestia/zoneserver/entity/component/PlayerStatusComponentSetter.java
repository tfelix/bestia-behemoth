package net.bestia.zoneserver.entity.component;

import java.util.Objects;

import net.bestia.model.domain.PlayerBestia;

/**
 * This setter prefills the {@link StatusComponent} with the current HP and Mana
 * of a persisted {@link PlayerBestia} from the database.
 * 
 * @author Thomas Felix
 *
 */
public class PlayerStatusComponentSetter extends ComponentSetter<StatusComponent> {
	
	private final PlayerBestia playerBestia;

	public PlayerStatusComponentSetter(PlayerBestia playerBestia) {
		super(StatusComponent.class);
		
		this.playerBestia = Objects.requireNonNull(playerBestia);
	}

	@Override
	protected void performSetting(StatusComponent comp) {
		
		comp.setUnmodifiedElement(playerBestia.getOrigin().getElement());
		comp.setStatusValues(playerBestia.getStatusValues());
	}

}
