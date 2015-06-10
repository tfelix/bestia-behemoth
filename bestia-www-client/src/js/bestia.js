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
 */
Bestia.Urls = {
	loginHtml : 'http://localhost/login.html',
	gameHtml: 'http://localhost/index.html',
	
	bestiaLogin: 'http://localhost:8090',
	bestiaSocket: 'http://localhost:8080/api'
};
