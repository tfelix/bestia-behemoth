

/**
 * Message can be used in order to retrieve a reference.
 */
export default class GetRefMessage {
	
	constructor(name, callback) {
		
		this.name = name;
		this.callback = callback;
	}
	
	
}