package net.bestia.messages;

import net.bestia.entity.component.Component;

/**
 * Messages marked with this interface provide some information to what kind of
 * component actor they message should be directed.
 * 
 * @author Thomas Felix
 *
 */
public interface ComponentMessage extends EntityMessage {

	/**
	 * This is the class type of the component this message is aimed to. The
	 * bestia messaging system will forward this message to the actor which is
	 * processing this component messages.
	 * 
	 * @return The string representation of the component class name.
	 */
	Class<? extends Component> targetsComponent();
}
