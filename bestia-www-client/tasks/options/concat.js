module.exports = {

	compile : {
		options : {
			sourceMap : true
		},
		files : [ {
			src : [ '<%= appFilelist %>' ],
			dest : '<%= buildDir %>/js/behemoth.js'
		} ]

	}

};