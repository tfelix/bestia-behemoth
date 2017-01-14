


export default class EngineMessage {
	
	constructor(data) {

		this.data = data;
	}
	
	get topic() {
		throw 'Must be overwritten by child.';
	}
}