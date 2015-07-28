module.exports = {
	options : {
		sourceMap : false,
		sourceMapName : '<%= buildDir %>/<%= filename %>.map',
		banner : '/*! BESTIA BEHEMOTH v.<%= version %> <%= grunt.template.today("yyyy-mm-dd") %> */\n',
		mangle : true,
	},
	compile : {
		files : {
			'<%= buildDir %>/js/lib-app.min.js' : [ 'build/js/lib-app.js' ],
			'<%= buildDir %>/js/lib-pages.min.js' : [ 'build/js/lib-pages.js' ],
			'<%= buildDir %>/js/behemoth.min.js' : [ 'build/js/behemoth.js' ]
		}
	}
};