package de.tfelix.bestia.ai.state;

public class SimpleState extends State {

	private static final long serialVersionUID = 1L;
	private final String stateName;

	public SimpleState(String name) {

		this.stateName = name;
	}

	public String getStateName() {
		return stateName;
	}

}
