module.exports = {
	systemjs : {
		options : {
			sfx : true,
			baseURL : "<%= tempDir %>/js",
			// configFile : "./target/config.js",
			minify : false,
			sourceMaps : true
		},
		
		files : [ {
			src : "main.js",
			dest : "<%= buildDir %>/js/behemoth.js"
		} ]
	}
};