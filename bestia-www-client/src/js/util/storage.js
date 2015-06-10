
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
	Cookies.set('auth', JSON.stringify({id: data.accId, token: data.token}));
};

/**
 * Returns the authorization object. Can be used to authenticate against the
 * zone/web server if a new connection has to be made.
 * 
 * @method Bestia.Storage#getAuth
 * @returns {Object} Authorization object.
 */
Bestia.Storage.prototype.getAuth = function() {
	return JSON.parse(Cookies.get('auth'));
};