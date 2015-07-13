module.exports = {
	all : {
		src : [ '<%= appFilelist %>' ],
		options : {
			specs : 'specs/**/*Spec.js',
			vendor : '<%= compile_dir %>/js/lib.js'
		},
		keepRunner : true
	}

};