module.export = {
	stylesheets : {
		files : [ 'source/css/**/*.less', 'source/css/**/*.css' ],
		tasks : [ 'css:dev' ]
	},
	copy : {
		files : [ 'source/**', '!source/**/*.styl', '!source/**/*.coffee', '!source/**/*.jade' ],
		tasks : [ 'default' ]
	}
};