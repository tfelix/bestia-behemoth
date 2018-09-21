package net.bestia.entity.component;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.entity.component.StatusComponent;

import java.util.Objects;

/**
 * Sets the status values based on a mob bestia from the database.
 * 
 * @author Thomas Felix
 *
 */
public class MobStatusComponentSetter extends ComponentSetter<StatusComponent> {

	private final Bestia bestia;
	
	public MobStatusComponentSetter(Bestia bestia) {
		super(StatusComponent.class);
		
		this.bestia = Objects.requireNonNull(bestia);
	}

	@Override
	protected void performSetting(StatusComponent comp) {
		
		comp.setUnmodifiedElement(bestia.getElement());
		comp.setOriginalStatusPoints(bestia.getStatusPoints());
	}

}
