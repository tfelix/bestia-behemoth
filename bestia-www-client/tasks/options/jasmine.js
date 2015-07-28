module.exports = {
	all : {
		src : [ '<%= appFilelist %>' ],
		options : {
			specs : 'specs/**/*Spec.js',
			vendor : ['<%= buildDir %>/js/lib-app.js', 'vendor/jasmine-jquery/lib/jasmine-jquery.js']
		},
		keepRunner : true
	}

};