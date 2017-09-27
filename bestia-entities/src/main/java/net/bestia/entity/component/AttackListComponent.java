package net.bestia.entity.component;

import java.util.HashSet;
import java.util.Set;


public class AttackListComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	private final Set<Integer> knownAttacks = new HashSet<>();

	public AttackListComponent(long id) {
		super(id);
		// no op.
	}

	public boolean contains(int attackId) {
		return knownAttacks.contains(attackId);
	}
}
