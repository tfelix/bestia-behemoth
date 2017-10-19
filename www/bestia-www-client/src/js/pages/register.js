import 'zepto';
import UrlHelper from '../util/UrlHelper.js';


$(document).ready(function() {

	$('#register').on('submit', function () {

		$.get(UrlHelper.restUrl + 'v1/account/check', {username: 'test', email: 'bla@bla.de'}, function(){
			console.log('geht');
		});

		alert("Not implemented.");

		return false;
	});
});
