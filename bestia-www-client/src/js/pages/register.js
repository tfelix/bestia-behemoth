import 'zepto';
import Urls from '../Urls.js';



$(document).ready(function() {

	$('#test').click(function(){
		$.get('http://localhost:8080/v1/account/check', {username: 'test', email: 'bla@bla.de'}, function(){
			console.log('geht');
		});
	});
});
