module.exports = {
	css : {
		files : [ '<%= sourceDir %>/css/**/*.less', '<%= sourceDir %>/css/**/*.css' ],
		tasks : [ 'less' ]
	},
	
	//js: {
	//	files : ['<%= sourceDir %>/js/**/*.js'],
	//	tasks : [ 'compile']
	//},
	
	html: {
		files : ['<%= sourceDir %>/*.html', '<%= sourceDir %>/**/*.json'],
		tasks : [ 'preprocess:htmldev', 'copy:dist']
	},
	
	asset : {
		files : ['../game-data/**/*'],
		tasks : [ 'copy:gamedata' ]
	}
};