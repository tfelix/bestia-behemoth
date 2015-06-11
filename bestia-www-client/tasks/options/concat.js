module.exports = {

	compile : {
		options : {
			sourceMap : true
		},
		// Custom scripts. Order is important!
		files : [ {
			src : [ '<%= appFilelist %>' ],
			dest : '<%= compile_dir %>/js/<%= filename %>.js'
		}, {
			src : [ '<%= compile_dir %>/js/lib-pages.js', '<%= pageFilelist %>' ],
			dest : '<%= compile_dir %>/js/app-pages.js'
		} ]

	}

};