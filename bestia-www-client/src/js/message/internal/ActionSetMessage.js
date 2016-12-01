
const MID = 1;

/**
 * This message is used to communicate actions to the input system. With this
 * actions the input system is able to react upon input.
 */
export default class ActionSetMessage {
	
	constructor() {
		
	}
	
	static get MID() {
		return MID;
	}
	
}