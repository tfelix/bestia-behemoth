/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

import Signal from '../io/Signal.js';
import guid from '../util/Guid.js';

/**
 * This class can be used to send translation requests to the server. Responses
 * from the server will be cached here. Since the whole communication is
 * asynchronous the insertion of the requested translations must be done via
 * callbacks.
 * 
 * @constructor
 * @class Bestia.I18N
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber reference.
 */
export default class I18n {
	
	constructor(pubsub) {
	
		this._cache = {};
	
		this._pubsub = pubsub;
	
		this._callback = {};
	
		/**
		 * @property Holds the current language of the user.
		 * @private
		 */
		this._lang = 'de-de';
		
		pubsub.subscribe('i18n.lang', this._languageChangedHandler.bind(this));
		pubsub.subscribe('translation.response', this._translationReceivedHandler.bind(this));
	}

	/**
	 * Private helper function which can access the cache via its argument. Will
	 * be delivered to the callback of the t method. The user can feed it with
	 * an 'cat.key' string to retrieve the translation.
	 * 
	 * @private
	 */
	_translator(arg) {
		var catKey = this.getCatAndKey(arg);
	
		var cat = this._cache[catKey[0]];
	
		if (cat === undefined) {
			return '';
		}
	
		var value = cat[catKey[1]];
	
		if (value === undefined) {
			return '';
		}
	
		return value;
	}

	_translationReceivedHandler(_, data) {
		if (!this._callback.hasOwnProperty(data.t)) {
			// Dunno... maybe wrong key? Not responsible.
			return;
		}
	
		// Add the returned items to our cache.
		for (var i = 0; i < data.is.length; i++) {
			var item = data.is[i];
	
			if (!this._existsCatKey(item.c, item.k)) {
				this._cache[item.c] = this._cache[item.c] || {};
				this._cache[item.c][item.k] = item.v;
			}
		}
	
		this._triggerCallback(data.t);
	}

	/**
	 * Handler which will reset the cache if the user has changed the language.
	 */
	_languageChangedHandler(_, data) {
		if (this._lang === data) {
			return;
		}
	
		this._lang = data;
		// Delete all caches.
		delete this._cache;
		this._cache = {};
		this._callback = {};
	}

	/**
	 * 
	 */
	_triggerCallback(token) {
		var cachedData = this._callback[token];
		delete this._callback[token];
		cachedData.fn();
	}

	_existsCatKey(cat, key) {
		if (!this._cache.hasOwnProperty(cat)) {
			return false;
		}
		return this._cache[cat].hasOwnProperty(key);
	}

	/**
	 * Returns the translated value of the cat and key. Or null if the value
	 * does not exist.
	 */
	_getValue(cat, key) {
		if (!this._existsCatKey(cat, key)) {
			return null;
		}
		return this._cache[cat][key];
	}
	
	/**
	 * Central translation function. Call this function with giving a key and a
	 * callback function which should be triggered as soon as the translation is
	 * received.
	 */
	t(key, fn) {
	
		// No callback. We can only deliver if we have already a cached value.
		// Because no callback.
		if (fn === undefined) {	
			return;
		}
	
		// Normalize the input.
		var data = [];
	
		// Generate a cat and key from the input.
		if (Array.isArray(key)) {
			for (var i = 0; i < key.length; i++) {
				var catKey = this.getCatAndKey(key[i]);
				if (catKey == null) {
					continue;
				}
				data.push({
					cat : catKey[0],
					key : catKey[1]
				});
			}
	
		} else {
			// No array.
			var catKey = this.getCatAndKey(key);
			if (catKey == null) {
				return;
			}
			data.push({
				cat : catKey[0],
				key : catKey[1]
			});
		}
	
		this._translationRequested(null, data, fn);
	}
	
	/**
	 * Make some sanity checks for the data. The data should be an array of
	 * strings to be translated.
	 */
	_translationRequested(_, data, fn) {
	
		var self = this;
		var uuid = guid();
	
		// Switch the case. If the returned items are only one then return a
		// simple translator function.
		var callback;
		if (data.length === 1) {
			// On single poll of translations simply return the asked string.
			var ident = data[0].cat + '.' + data[0].key;
			callback = function() {
				fn(function() {
					return self._translator(ident);
				});
			};
		} else {
			callback = function() {
				fn(self._translator);
			};
		}
	
		// Save callback for async return.
		this._callback[uuid] = {
			fn : callback
		};
	
		var newDataItems = [];
	
		// Check if we have already cached translated items for the same key.
		// Remove them from the request.
		for (var i = 0; i < data.length; i++) {
			if (!this._existsCatKey(data[i].cat, data[i].key)) {
				newDataItems.push(data[i]);
			}
		}
	
		if (newDataItems.length === 0) {
			self._triggerCallback(uuid);
		}
	
		var msg = new Bestia.Message.TranslationRequest(newDataItems, uuid);
		this._pubsub.publish(Signal.IO_SEND, msg);
	}
	
	/**
	 * The value is usually in the form of: cat.key and will be split to [cat,
	 * key] as the return.
	 * 
	 * @param {String}
	 *            val - Value which will be seperated to category and key.
	 * @method Bestia.I18n#getCatAndKey
	 * @private
	 * @return {Array} - An array in the form: [cat, key]. Or null if the val
	 *         was not valid.
	 */
	getCatAndKey(val) {
		var i = val.indexOf('.');
		if (i === -1) {
			return null;
		}
	
		var cat = val.substring(0, i).toUpperCase();
		var key = val.substring(i + 1);
	
		return [ cat, key ];
	}
}