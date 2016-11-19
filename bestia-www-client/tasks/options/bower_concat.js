module.exports = {
	app : {
		exclude : [ 'jasmine-jquery' ],
		dest : '<%= buildDir %>/js/lib-app.js',
		cssDest : '<%= buildDir %>/css/lib-app.css'
	},

	page : {
		include : [ 'jquery', 'i18next', 'js-cookie'],
		dest : '<%= buildDir %>/js/lib-pages.js'
	}
};