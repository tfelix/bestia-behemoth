module.exports = {

	dist : {
		files : [ {
			expand : true,
			cwd : '<%= sourceDir %>',
			// Exclude HTML files copy them again will conflict with the
			// preprocess task.
			src : [ '**/*', '!css/**', '!js/**', '!*.html'],
			dest : '<%= buildDir %>'
		} ]
	},

	gamedata : {
		files : [ {
			expand : true,
			cwd : '../game-data',
			src : '**',
			dest : 'build/assets'
		} ]
	}
};