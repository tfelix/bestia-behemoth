//Final code.
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	$('body').i18n();
});

$(document).ready(function() {
	// Login Form vorbereiten.
	$('#login').submit(function() {
		var email = $('#inputEmail').val();
		var password = $('#inputPassword').val();

		$.get(Bestia.Urls.bestiaLogin + "/v1/account/login", {
			ident : email,
			password : password
		}, function(data) {

			// Save cookie with this data.
			var storage = new Bestia.Storage();
			storage.storeAuth(data);

			window.location.href = Bestia.Urls.gameHtml;
		}).fail(function() {
			$('#login').addClass('has-error');
		});

		return false;
	});
});
