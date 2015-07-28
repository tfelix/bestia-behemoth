module.exports = {
	compile : {
		dest : '<%= buildDir %>/js/lib-app.js',
		cssDest : '<%= buildDir %>/css/lib-app.css'
	},
	
	compilePage : {
		include: ['jquery', 'i18next', 'bootstrap', 'html5shiv', 'js-cookie', 'knockout'],
		dest : '<%= buildDir %>/js/lib-pages.js',
		cssDest : '<%= buildDir %>/css/lib-pages.css'
	}
};