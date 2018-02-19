package bestia.entity.component;

import java.util.Objects;

import bestia.model.domain.Bestia;

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
