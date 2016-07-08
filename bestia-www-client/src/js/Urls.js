/**
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * @copyright 2015 Thomas Felix
 */

/**
 * Holds various hard coded URLs. These might be different in production and in
 * local use. They will be replaced by the build system upon release.
 * 
 * @constant
 * @property {string} Urls.loginHtml - URL of the login page.
 * @property {string} Urls.gameHtml - URL of the main game page.
 * @property {string} Urls.bestiaWebAPI - URL of the login API endpoint.
 * @property {string} Urls.bestiaWebsocket - URL of the websocket API endpoint.
 */
var Urls = {

	// @ifdef DEVELOPMENT
	loginHtml : 'http://localhost/login.html',
	gameHtml : 'http://localhost/index.html',
	bestiaWebAPI : 'http://localhost:8090',
	bestiaWebsocket : 'http://localhost:8080/api',
	assetsRoot : 'http://localhost/assets/'
	// @endif

};

export default { Urls };