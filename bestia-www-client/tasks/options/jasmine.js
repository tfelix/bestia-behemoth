module.export = {
	all : {
		src : ['<%= filelist %>'],
		options : {
			specs : 'specs/**/*Spec.js',
			vendor : 'build/js/libs.js'
		},
		keepRunner : true
	}
}