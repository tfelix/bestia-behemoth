/**
 * preloadjs 0.1.2
 * https://github.com/CristianMR/preloadjs
 * (c) Cristian Martín Rios & Heber Lopez 2014 | License MIT
 */
(function(window){

    function preload(config){

        var defaultConfig = {
          map: [],
          version: 1,
          debug: false
        };

        config = _.extend({}, defaultConfig, config);
        var map = config.map;
        var version = config.version;
        var debug = config.debug;

        //Only show internal console, if is in debug mode
        var console = function(debug){
            if(!debug) return {log: function(){}, error: function(){}};
            return {log: _.bind(window.console.log, window.console), error: _.bind(window.console.error, window.console)}
        }(debug);

        var body = document.getElementsByTagName('body')[0];

        //If there is no localStorage, then object
        var storage = typeof localStorage !== "undefined" ? localStorage : {};

        var modsLoaded = [];
        var modsInitiated = [];
        var modsWaitingForScriptsToInit = [];
        var callbackModList = {};

        //Delete scripts in localStorage, if not equals version of app
        if(storage[withPrefix('version')] && parseFloat(storage[withPrefix('version')]) !== version){
          _.forIn(localStorage, function(value, key){
            //Checks whether the key starts with "pl_", if it does, removes the item from the localStorage
            if(/^pl_.+$/.test(key)){
              console.log('removed script from local storage', key);
              localStorage.removeItem(key);
            }
          })
        }

        //Saving map and version in localStorage
        storage[withPrefix('version')] = version;

        //forIn of modules
        _.forIn(map, function(object, module){

            //With _.after, only execute this function the last time
            //With _.once, only can execute this function one time
            var done = _.after(_.size(object.scripts), _.once(function(module){

                //Add module to list of mods loaded, and load it !
                console.log("module loaded", module);
                modsLoaded.push(module);
                if(map[module].initOnLoad || _.indexOf(modsWaitingForScriptsToInit, module) !== -1)
                    initModule(module);
            }));

            if(object.allInOne && object.allInOne.url)
                loadAllInOne(object, module, done);
            else
                loadUrls(object.scripts, module, done);
        });

        function loadAllInOne(object, module, done){

            //Clone the object without reference to the original
            var urls = _.cloneDeep(object.scripts, true);

            //if exist in local storage, remove and call to done(module)
            var urls_removed = _.remove(urls, function(value){
                return !_.isUndefined(storage[withPrefix(value)]);
            });

            _.forEach(urls_removed, function(url){
                done(module);
            });

            if(_.size(urls) === 0) return;

            var callback = function(status, data){
                if(status !== 200) return console.error('Module allInOne', module,'not loaded');
                console.log('Module allInOne', module, 'downloaded');
                if(typeof data === 'string') data = JSON.parse(data);
                _.forIn(data, function(data, url){
                    storage[withPrefix(url)] = data;
                    done(module);
                });
            };

            var postData = JSON.stringify({
                urls: urls
            });

            var headers = {
                'content-type':'application/json'
            };

            ajax('POST', object.allInOne.url, callback, postData, headers);
        }

        function loadUrls(urls, module, done){
            _.forEach(urls, function(url){
                if(storage[withPrefix(url)]) done(module);
                else getScript(url, done, module);
            })
        }

        function getScript(url, done, module){
          ajax('GET', url, function(status, data){
            if(status !== 200) return done(module);
            console.log("url load", url);
            storage[withPrefix(url)] = data;
            done(module);
          });
        }

        function initModule(name){//TODO add external visibility
            console.log('try to init module', name);

            //If the module was initiated, stop
            if(_.indexOf(modsInitiated, name) !== -1) return;

            //Is there any dependency? If answer is yes, stop.
            if(map[name].dependencies && checkModuleDependency(name)) return;

            //If the module was not loaded, add to waiting for scripts to init, and stop
            if(_.indexOf(modsLoaded, name) === -1){
                console.log('Mod',name, 'is still waiting for scripts to init');
                if(_.indexOf(modsWaitingForScriptsToInit, name) === -1) modsWaitingForScriptsToInit.push(name);
                return;
            }

            //Add scripts to body
            _.forEach(map[name].scripts, function(url){
                var script = document.createElement('script');
                script.textContent = storage[withPrefix(url)];
                body.appendChild(script);
            });

            if(map[name].isAngularModule)//TODO Only for one module, otherwise KABOOM!. The integration with Angular is still in progress
                angular.element(document).ready(function() {
                    console.log('isAngularModule', name);
                    angular.bootstrap(document, [name]);
                });

            console.log("module initiated", name);
            //To initiated-modules list
            modsInitiated.push(name);

            _.pull(modsWaitingForScriptsToInit, name);

            //Call the modules which have this module as dependency
            if(callbackModList[name]){
                console.log('callbackModList', name);
                _.forEach(callbackModList[name], function(module){
                    initModule(module);
                });
                delete callbackModList[name];
            }
        }

        function checkModuleDependency(name){
            var haveDependencies = false;
            var dependencies = map[name].dependencies;
            if(dependencies)
                _.forEach(dependencies, function(module){
                    if(_.indexOf(modsInitiated, module) === -1){
                        haveDependencies = true;
                        if(!callbackModList[module]) callbackModList[module] = [];
                        callbackModList[module].push(name);//Add to the list of modules to call when load finish
                        initModule(module);
                    }
                });
            return haveDependencies;
        }

      function withPrefix(string){
        return 'pl_' + string;
      }

    }

    /**
     * Copyright (c) 2010-2014 Google, Inc. http://angularjs.org | MIT
     * taken from https://github.com/angular/angular.js/blob/master/src/ng/httpBackend.js
     */
    function ajax(method, url, callback, post, headers, withCredentials, responseType) {

      responseType = responseType ? responseType : 'text';

      var xhr = new window.XMLHttpRequest();

      xhr.open(method, url, true);
      _.forIn(headers, function(value, key) {
        if (!_.isUndefined(value)) {
          xhr.setRequestHeader(key, value);
        }
      });

      xhr.onload = function requestLoaded() {
        var statusText = xhr.statusText || '';

        // responseText is the old-school way of retrieving response (supported by IE8 & 9)
        // response/responseType properties were introduced in XHR Level2 spec (supported by IE10)
        var response = ('response' in xhr) ? xhr.response : xhr.responseText;

        // normalize IE9 bug (http://bugs.jquery.com/ticket/1450)
        var status = xhr.status === 1223 ? 204 : xhr.status;

        // fix status code when it is 0 (0 status is undocumented).
        // Occurs when accessing file resources or on Android 4.1 stock browser
        // while retrieving files from application cache.
        if (status === 0) {
          status = response ? 200 : /^file.+$/.test(url) ? 404 : 0;
        }

        completeRequest(callback,
            status,
            response,
            xhr.getAllResponseHeaders(),
            statusText);
      };

      var requestError = function() {
        // The response is always empty
        // See https://xhr.spec.whatwg.org/#request-error-steps and https://fetch.spec.whatwg.org/#concept-network-error
        completeRequest(callback, -1, null, null, '');
      };

      xhr.onerror = requestError;
      xhr.onabort = requestError;

      if (withCredentials) {
        xhr.withCredentials = true;
      }

      if (responseType) {
        try {
          xhr.responseType = responseType;
        } catch (e) {
          // WebKit added support for the json responseType value on 09/03/2013
          // https://bugs.webkit.org/show_bug.cgi?id=73648. Versions of Safari prior to 7 are
          // known to throw when setting the value "json" as the response type. Other older
          // browsers implementing the responseType
          //
          // The json response type can be ignored if not supported, because JSON payloads are
          // parsed on the client-side regardless.
          if (responseType !== 'json') {
            throw e;
          }
        }
      }

      xhr.send(post || null);

      function completeRequest(callback, status, response, headersString, statusText) {
        xhr = null;

        callback(status, response, headersString, statusText);
      }
    }

    window.preload = preload;
})(window);