module.exports = {
	all : {
		src : [ '<%= appFilelist %>' ],
		options : {
			specs : 'specs/**/*Spec.js',
			vendor : ['<%= compile_dir %>/js/lib-app.js', 'src/js/lib/jasmine-jquery/lib/jasmine-jquery.js']
		},
		keepRunner : true
	}

};