module.export = {
	options : {
		sourceMap : true,
		sourceMapName : '<%= compile_dir %>/<%= filename %>.map',
		banner : '/*! BESTIA BEHEMOTH v.<%= package.version %> <%= grunt.template.today("yyyy-mm-dd") %> */\n'
	},
	build : {
		src : 'src/<%= pkg.name %>.js',
		dest : '<%= compile_dir %>/<%= filename %>.min.js'
	}
};