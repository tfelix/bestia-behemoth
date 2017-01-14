/*global Phaser */

import PubSub from '../util/PubSub';
import Signal from '../io/Signal';
import getRefName from './ReferenceName';
import EngineMessage from './../message/internal/EngineMessage';


export default class EnginePubSub extends PubSub {
	

	constructor() {
		super();
		
		/**
		 * Cache for the callbacks.
		 */
		this._components = [];
		
		/**
		 * If a component is requested but not yet registered the call will be
		 * cached until this happend and then it will resolve.
		 */
		this._callbackCache = [];
	}
	
	/**
	 * Checks if all refs are registered. If all are registered true is returned
	 * false otherwise.
	 */
	hasRefs(refs) {
		if(!Array.isArray(refs)) {
			refs = [refs];
		}
		
		let names = this._components.map(function(v){
			return v.name;
		});
		
		for(let i = 0; i < refs.length; i++) {
			if(names.indexOf(refs[i].ref) === -1) {
				return false;
			}
		}
		
		return true;
	}
	
	publish(obj, data) {
		if(!(obj instanceof EngineMessage)) {
			// Seems its a normal message let parent handle it.
			super.publish(obj, data);
		} else {
			super.publish(obj.topic, data);
		}
	}
	
	/**
	 * Does an async callback request to the cache system to retrieve the
	 * reference to the requested object.
	 */
	getRef(fnRefs, callback) {
		if(!Array.isArray(fnRefs)) {
			fnRefs = [fnRefs];
		}
		
		let cacheObj = {refs: fnRefs, callback: callback};
		
		let buffer = [];
		
		for(let i = 0; i < cacheObj.refs.length; i++) {
			
			let name = cacheObj.refs[i];
			
			for(let j = 0; j < this._components.length; j++) {
				
				if(this._components[j].name === name) {
					buffer.push(this._components[j].ref);
					break;
				}
			}
		}
		
		if(buffer.length !== cacheObj.refs.length) {
			this._callbackCache.push(cacheObj);
			return;
		} else {
			if(fnRefs.length == 1) {
				callback(buffer[0]);
			} else {
				callback(buffer);
			}
		}
	}
	
	_checkCallbackCache() {
		
	}
	
	
	extendRefs(refs, obj) {
		
		if(!this.hasRefs(refs)) {
			throw 'Not all references where instanced.';
		}
		
		if(!Array.isArray(refs)) {
			refs = [refs];
		}
		
		refs.forEach(ref => {
			this.getRef(ref.ref, data => {
				// find the name of the new member.
				for(let i = 0; i < refs.length; i++) {
					if (refs[i].ref === ref.ref) {
						obj[refs[i].member] = data;
						break;
					}
				}
			});
		});
	}
	
	setRef(refName, ref) {
		
		this._components.push({name: refName, ref: ref});
		this._checkCallbackCache();
	}
}