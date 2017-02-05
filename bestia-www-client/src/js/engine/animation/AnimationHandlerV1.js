import LOG from '../../util/Log';

export default class AnimationHandlerV1 {
	
	constructor(data) {

		this.HANDLES_VERSION = 1;
		
		// Sanity checks.
		if(data.version != this.HANDLES_VERSION) {
			throw 'Version of animation does not match handled version.';
		}
		
		this._data = data;
		
		/**
		 * Holds entity which are registered by the handler.
		 */
		this._entityCache = {};
		
	}
	
	registerEntity(id, entity) {
		this._entityCache[id] = entity;
	}
	
	getEntity(id) {
		if(this._entityCache.hasOwnProperty(id)) {
			return this._entityCache[id];
		} else {
			return null;
		}
	}
	
	_playSpriteAnimation() {
		
	}
	
	loadResources() {
		if(!this._data.hasOwnProperty('resources')) {
			return;
		}
		
		for(let i = 0; i < this._data.resources.length; i++) {
			let res = this._data.resources[i];
			
			switch(res.type.toUpperCase()) {
			case 'IMAGE':
				
				break;
			case 'SOUND':
				
				break;
			default:
				LOG.warn('Unsupported resource type: {}', res.type);
			break;
			}
		}
	}

	createAnimation(origin, target) {
		
		
		
	}

	
}
