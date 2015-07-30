module.exports = {

	dist : {
		files : [ {
			expand : true,
			cwd : '<%= sourceDir %>',
			src : [ 'img/**', 'locales/**' ],
			dest : 'build'
		}]
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