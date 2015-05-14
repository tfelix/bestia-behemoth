module.exports = {
	all : {
		src : ['<%= filelist %>'],
		options : {
			specs : 'specs/**/*Spec.js',
			vendor : '<%= compile_dir %>/js/lib.js'
		},
		keepRunner : true
	}
};