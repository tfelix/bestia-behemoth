module.export = {
	css : {
		files : [ '<%= source_dir %>/css/**/*.less', '<%= source_dir %>/css/**/*.css' ],
		tasks : [ 'compile-css' ]
	},
	copy : {
		files : [ '<%= source_dir %>/**' ],
		tasks : [ 'copy' ]
	}
};