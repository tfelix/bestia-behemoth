module.exports = {
	css : {
		files : [ '<%= source_dir %>/css/**/*.less', '<%= source_dir %>/css/**/*.css' ],
		tasks : [ 'compile-css' ]
	},
	
	html: {
		files : ['<%= source_dir %>/*.html'],
		tasks : [ 'copy']
	},
	
	compile : {
		files : [ '<%= source_dir %>/js/**/*.js' ],
		tasks : [ 'compile-js' ]
	}
};