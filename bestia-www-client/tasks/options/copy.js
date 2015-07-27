module.exports = {

	dist : {
		files : [ {
			expand : true,
			cwd : '<%= source_dir %>',
			src : [ '**', '!js/**', '!css/**' ],
			dest : 'build'
		}, {
			expand : true,
			flatten : true,
			cwd : '<%= source_dir %>',
			src : 'js/pages/single/*.js',
			dest : 'build/js'
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