import 'zepto';
import UrlHelper from '../util/UrlHelper.js';
import Storage from '../util/Storage.js';


$(document).ready(function () {

	// Login Form vorbereiten.
	$('#login').on('submit', function () {
		var email = $('#username').val();
		var password = $('#password').val();

		$.get(UrlHelper.restUrl + 'v1/account/login', {
			accName: email,
			password: password
		}, {
				success: function (data) {

					// Save cookie with this data.
					var storage = new Storage();
					storage.storeAuth(data);

					// Redirect to the game itself.
					window.location.href = UrlHelper.gameUrl;
				},
				error: function (data) {
					alert("fehler");
				}
			}
		);

		return false;
	});

});

