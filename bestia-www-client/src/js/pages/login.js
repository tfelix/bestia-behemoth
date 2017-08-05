import 'zepto';
import ko from 'knockout';
import UrlHelper from '../util/UrlHelper.js';
import Storage from '../util/Storage.js';


$(document).ready(function () {

	// Ask the server about the login status.
	$.get(UrlHelper.restUrl + 'v1/server/status', function (data) {

		if (data.maintenance.toUpperCase() !== 'NONE') {

			// Server is in maintenance mode.
			$('#maintenance').show();
			$('#username').disable();
			$('#password').disable();
		}
	});

	// Login Form vorbereiten.
	$('#login').on('submit', function () {
		var email = $('#username').val();
		var password = $('#password').val();

		$.get(UrlHelper.restUrl + 'v1/account/login', {
			accName: email,
			password: password
		},
			function (data) {

				if (data.success) {

					// Save cookie with this data.
					var storage = new Storage();
					storage.storeAuth(data);

					// Redirect to the game itself.
					window.location.href = UrlHelper.gameUrl;
				} else {
					$('#error').text('Could not login.');
				}
			}
		);

		return false;
	});

});

