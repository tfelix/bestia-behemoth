/**
 * This is only an example
 *
 * To run this example you need NodeJS.
 *
 * npm install (to install package dependencies)
 * node client_server (to run server)
 *
 * You can change the route to bower_components, or install bower in preloadjs/
 */
var fs = require("fs");
var path = require("path");
var bodyParser = require('body-parser');
var express = require('express');
var app = express();
var _ = require('lodash');
var port = 3000;
var developmentMode = true;

var Promise = require("bluebird");
var readFile = Promise.promisify(require("fs").readFile);//Add promises to fs only for readFile function


app.use('/bower_components', express.static(path.join(__dirname, '../../bower_components')));//Take bower_components from preloadjs/bower_components
app.use(/^.*preload(?:.min)?.js$/, function(req, res, next){
	//If you are development in this project, you want this here
	var fileDirAndName = developmentMode ? 'src/preload.js' : 'dist/preload.min.js';
	res.sendFile(path.join(__dirname, '../../', fileDirAndName));
});
app.use(express.static(__dirname + '/app'));

app.use(bodyParser.json());

//Read all files and return data in one request
app.post('/allInOne', function(req, res, next){
	var urls = req.body.urls ? req.body.urls : [];

	var data = {};

	var promises = _.map(urls, function(url){
		return readFile(path.join(__dirname, 'app', url), 'utf-8').then(function(content){
			data[url] = content;
		});
	});

	Promise.all(promises).then(function(){
		console.log("all loaded");
		res.send(data);
	}).catch(function(e) {
		res.status(503);
		console.error("Error reading file", e);
		res.send("Error reading file");
	});
});

app.listen(port, function(){
	console.log('Listen on port', port);
});