module.exports = {
	options : {
		sourceMap : true,
		sourceMapName : '<%= compile_dir %>/<%= filename %>.map',
		banner : '/*! BESTIA BEHEMOTH v.<%= package.version %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
	},
	compile : {
		files : {
			'build/js/lib-app.min.js' : [ 'build/js/lib-app.js' ],
			'build/js/lib-pages.min.js' : [ 'build/js/lib-pages.js' ]
		}
	}
};