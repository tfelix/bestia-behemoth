module.exports = {
	css : {
		files : [ '<%= sourceDir %>/css/**/*.less', '<%= sourceDir %>/css/**/*.css' ],
		tasks : [ 'less' ]
	},
	
	js: {
		files : ['<%= sourceDir %>/js/**/*.js', '!<%= sourceDir %>/js/pages/**'],
		tasks : [ 'preprocess:dev', 'jsonlint', 'jshint', 'systemjs']
	},
	
	jsPages: {
		files : ['<%= sourceDir %>/js/pages/**/*.js'],
		tasks : [ 'preprocess:dev', 'jsonlint', 'jshint', 'systemjs']
	},
	
	html: {
		files : ['<%= sourceDir %>/*.html', '<%= sourceDir %>/**/*.json'],
		tasks : [ 'preprocess:htmldev', 'copy:dist']
	},
	
	asset : {
		files : ['../game-data/**/*'],
		tasks : [ 'copy:gamedata' ]
	}
};