module.exports = {
	css : {
		files : [ '<%= source_dir %>/css/**/*.less', '<%= source_dir %>/css/**/*.css' ],
		tasks : [ 'compile-css' ]
	},
	
	js: {
		files : ['<%= source_dir %>/js/**/*.js'],
		tasks : [ 'compile-js']
	},
	
	page_js: {
		files : ['<%= source_dir %>/js/pages/single/*.js'],
		tasks : [ 'copy']
	},
	
	html: {
		files : ['<%= source_dir %>/*.html', '<%= source_dir %>/**/*.json'],
		tasks : [ 'copy']
	},
	
	asset : {
		files : ['../game-data/**/*'],
		tasks : [ 'copy:dist' ]
	}
};