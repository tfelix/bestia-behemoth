module.exports = {
	compile : {
		dest : '<%= compile_dir %>/js/lib-app.js',
		cssDest : '<%= compile_dir %>/css/lib-app.css'
	},
	
	compilePage : {
		include: ['jquery', 'i18next', 'bootstrap', 'html5shiv', 'js-cookie', 'knockout'],
		dest : '<%= compile_dir %>/js/lib-pages.js',
		cssDest : '<%= compile_dir %>/css/lib-pages.css'
	}
};