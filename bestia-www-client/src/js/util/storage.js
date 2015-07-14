
/**
 * Provides an API to access and store local data. Depending on the kind so
 * stored object local storage or a cookie will be used.
 * 
 * @class Bestia.Storage
 */
Bestia.Storage = function() {

};

/**
 * Stores a authorization object. This will be delivered by the login server and
 * hold a account id and a login token.
 * 
 * @method Bestia.Storage#storeAuth
 * @param {Object}
 *            data - Object with account id and login token.
 */
Bestia.Storage.prototype.storeAuth = function(data) {
	Cookies.set('auth', JSON.stringify(data));
};

/**
 * Returns the authorization object. Can be used to authenticate against the
 * zone/web server if a new connection has to be made.
 * 
 * @method Bestia.Storage#getAuth
 * @returns {Object} Authorization object or @{code null} if no object was persisted.
 */
Bestia.Storage.prototype.getAuth = function() {
	
	var data = Cookies.get('auth');
	
	if(data === undefined) {
		return null;
	}
	
	return JSON.parse(data);
};

/**
 * Clears the storage of all data which was stored inside.
 * 
 * @method Bestia.Storage#clear
 */
Bestia.Storage.prototype.clear = function() {
	Cookies.remove('auth');
};