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
	
	/* HTML DEVELOPMENT SET */
	htmldev : {
		src: '**/*.html',
		dest: '<%= buildDir %>',
		cwd: '<%= sourceDir %>',
		expand: true,
		options : {
			context : {
				DEVELOPMENT: true
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
	},
	
	dev : {
		src : 'js/**/*.js',
		dest : '<%= tempDir %>',
		cwd: 'src',
		expand: true,
		options : {
			context : {
				DEVELOPMENT: true
			}
		}
	}	
};