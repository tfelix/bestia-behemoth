module.exports = {

	dist : {
		files : [ {
			expand : true,
			cwd : 'source',
			src : [ '**', '!js/**', '!css/**' ],
			dest : 'build'
		}, {
			expand : true,
			cwd : '../game-data',
			src : '**',
			dest : 'build/assets'
		} ]
	}
};