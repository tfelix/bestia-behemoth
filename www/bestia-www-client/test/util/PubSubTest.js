//var should = require('should');
import PubSub from '../../src/js/util/PubSub';
import Signal from '../../src/js/io/Signal';

describe('PubSub', function () {
	describe('publish', function () {
		it('publishes with no arguments.', function (done) {
			var pubsub = new PubSub();
			pubsub.subscribe('test', function (topic, data) {
				topic.should.eql('test');
				//should.not.exist(data);
				done();
			});
			pubsub.publish('test');
		});

		it('publishes with arguments', function (done) {
			var pubsub = new PubSub();
			var d = {
				test: 123
			};
			pubsub.subscribe('test', function (topic, data) {
				topic.should.eql('test');
				data.should.eql(d);
				done();
			});
			pubsub.publish('test', d);
		});
	});

	describe('subscribe', function () {
		it('wont subscribe null/undefined topics', function () {
			(function () {
				var pubsub = new PubSub();
				pubsub.subscribe(null, function () {
					// no op.
				});
			}).should.throw();
		});
	});

	describe('send', function () {
		it('it publishes under Signal.IO_SEND_MESSAGE topic', function (done) {
			var pubsub = new PubSub();
			pubsub.subscribe(Signal.IO_SEND_MESSAGE, function () {
				done();
			});
			pubsub.send({
				ok: true
			});
		});
	});

	describe('unsubscribe', function () {
		it('can unsubsribe in a publish callback', function () {
			var pubsub = new PubSub();
			var called = false;

			var callbackErr = function () {
				called = true;
			};

			var callback = function () {
				pubsub.unsubscribe('test2', callbackErr);
			};

			pubsub.subscribe('test', callback);
			pubsub.subscribe('test2', callbackErr);
			pubsub.publish('test');
			pubsub.publish('test2');
			called.should.be.false;
		});

		it('can unsubscribe to one function.', function () {
			var pubsub = new PubSub();
			var called = false;
			var callback = function () {
				called = true;
			};
			pubsub.subscribe('test', callback);
			pubsub.unsubscribe('test', callback);
			pubsub.publish('test');
			called.should.be.false;
		});

		it('can unsubscribe to all functions.', function () {
			var pubsub = new PubSub();
			var called = false;
			var fn1 = function () {
				called = true;
			};
			var fn2 = function() {
				called = true;
			};
			pubsub.subscribe('test', fn1);
			pubsub.subscribe('test', fn2);
			pubsub.unsubscribe('test');
			pubsub.publish('test');
			called.should.be.false;
		});
	});
});