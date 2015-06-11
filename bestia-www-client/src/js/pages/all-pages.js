/**
 * General code which should be included on all pages.
 */

// Initialize i18next with the page namespace.
i18n.init({
	lng : "de",
	ns: 'page',
	fallbackLng : false
}, function() {
	$('body').i18n();
});

