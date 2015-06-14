/* global Bestia:true */
/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * @namespace Bestia
 */
var Bestia = Bestia || {};

/**
 * Holds various hard coded URLs. These might be different in production and in
 * local use. They will be replaced by the build system upon release.
 * 
 * @namespace Bestia.Urls
 */
Bestia.Urls = {
	/**
	 * @property URL of the login page.
	 */
	loginHtml : 'http://localhost/login.html',
	/**
	 * @property URL of the main game page.
	 */
	gameHtml : 'http://localhost/index.html',

	/**
	 * @property URL of the login API endpoint.
	 */
	bestiaWebAPI : 'http://localhost:8090',
	
	/**
	 * @property URL of the websocket API endpoint.
	 */
	bestiaSocket : 'http://localhost:8080/api'
};
