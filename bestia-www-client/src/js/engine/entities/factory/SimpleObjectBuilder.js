import Builder from './Builder.js';

/**
 * Responsible for building simple objects. This consists of particle emitters
 * etc. Maybe this will be broken down further.
 */
export default class SimpleObjectBuilder extends Builder {
	constructor(factory, ctx) {
		super(factory, ctx);
		
		// Register with factory.
		this.type = 'simpleobject';
		this.version = 1;
	
		this._data = null;
	}

	build(data) {
		if(data.onlyLoad) {
			return null;
		}
		
		this._data = data;
	}

	canBuild(data) {
		return data.type === this.type && data.version === this.version;
	}
	
	_getType(template) {
		// Check the type of the given template.
		for (var i = 0; i < this._data.data.length; i++) {
			if (this._data.data[i].name === template.name) {
				return this._data.data[i].type;
			}
		}
	
		return "unknown";
	}

	_createNode(template) {
		// Check the type of the given template.
		var type = this._getType(template);
	
		switch (type) {
		case 'particle':
	
			break;
		case 'sprite':
			return this._game.add.sprite(-100, -100, template.name);
		case 'filter':
			console.warn("Not yet supported");
			break;
		default:
			// no supported.
			return null;
		}
	}
}
