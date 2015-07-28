module.exports = {
	options : {

	},
	
	htmlprod : {
		src: '**/*.html',
		dest: '<%= buildDir %>',
		cwd: '<%= sourceDir %>',
		expand: true,
		options : {
			context : {
				PRODUCTION: true
			}
		}
	},

	prod : {
		src : 'js/**/*.js',
		dest : '<%= tempDir %>',
		cwd: 'src',
		expand: true,
		options : {
			context : {
				PRODUCTION: true
			}
		}
	}
};