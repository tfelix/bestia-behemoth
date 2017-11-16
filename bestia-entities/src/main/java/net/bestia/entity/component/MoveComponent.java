package net.bestia.entity.component;

/**
 * If this component is added to an entity it will start moving along the path
 * saved into this component. If the path is completely resolved the component is
 * removed.
 * 
 * @author Thomas Felix
 *
 */
public class MoveComponent extends Component {

	private static final long serialVersionUID = 1L;

	public MoveComponent(long id) {
		super(id);
		// TODO Auto-generated constructor stub
	}

}
