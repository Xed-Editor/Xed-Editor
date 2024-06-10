'use strict';

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var errors = require('./errors');
var debug = require('debug')('node-telegram-bot-api');
var https = require('https');
var http = require('http');
var fs = require('fs');
var bl = require('bl');

var TelegramBotWebHook = function () {
  /**
   * Sets up a webhook to receive updates
   * @param  {TelegramBot} bot
   * @see https://core.telegram.org/bots/api#getting-updates
   */
  function TelegramBotWebHook(bot) {
    _classCallCheck(this, TelegramBotWebHook);

    this.bot = bot;
    this.options = typeof bot.options.webHook === 'boolean' ? {} : bot.options.webHook;
    this.options.host = this.options.host || '0.0.0.0';
    this.options.port = this.options.port || 8443;
    this.options.https = this.options.https || {};
    this.options.healthEndpoint = this.options.healthEndpoint || '/healthz';
    this._healthRegex = new RegExp(this.options.healthEndpoint);
    this._webServer = null;
    this._open = false;
    this._requestListener = this._requestListener.bind(this);
    this._parseBody = this._parseBody.bind(this);

    if (this.options.key && this.options.cert) {
      debug('HTTPS WebHook enabled (by key/cert)');
      this.options.https.key = fs.readFileSync(this.options.key);
      this.options.https.cert = fs.readFileSync(this.options.cert);
      this._webServer = https.createServer(this.options.https, this._requestListener);
    } else if (this.options.pfx) {
      debug('HTTPS WebHook enabled (by pfx)');
      this.options.https.pfx = fs.readFileSync(this.options.pfx);
      this._webServer = https.createServer(this.options.https, this._requestListener);
    } else if (Object.keys(this.options.https).length) {
      debug('HTTPS WebHook enabled by (https)');
      this._webServer = https.createServer(this.options.https, this._requestListener);
    } else {
      debug('HTTP WebHook enabled');
      this._webServer = http.createServer(this._requestListener);
    }
  }

  /**
   * Open WebHook by listening on the port
   * @return {Promise}
   */


  _createClass(TelegramBotWebHook, [{
    key: 'open',
    value: function open() {
      var _this = this;

      if (this.isOpen()) {
        return Promise.resolve();
      }
      return new Promise(function (resolve, reject) {
        _this._webServer.listen(_this.options.port, _this.options.host, function () {
          debug('WebHook listening on port %s', _this.options.port);
          _this._open = true;
          return resolve();
        });

        _this._webServer.once('error', function (err) {
          reject(err);
        });
      });
    }

    /**
     * Close the webHook
     * @return {Promise}
     */

  }, {
    key: 'close',
    value: function close() {
      var _this2 = this;

      if (!this.isOpen()) {
        return Promise.resolve();
      }
      return new Promise(function (resolve, reject) {
        _this2._webServer.close(function (error) {
          if (error) return reject(error);
          _this2._open = false;
          return resolve();
        });
      });
    }

    /**
     * Return `true` if server is listening. Otherwise, `false`.
     */

  }, {
    key: 'isOpen',
    value: function isOpen() {
      // NOTE: Since `http.Server.listening` was added in v5.7.0
      // and we still need to support Node v4,
      // we are going to fallback to 'this._open'.
      // The following LOC would suffice for newer versions of Node.js
      // return this._webServer.listening;
      return this._open;
    }

    /**
     * Handle error thrown during processing of webhook request.
     * @private
     * @param  {Error} error
     */

  }, {
    key: '_error',
    value: function _error(error) {
      if (!this.bot.listeners('webhook_error').length) {
        return console.error('error: [webhook_error] %j', error); // eslint-disable-line no-console
      }
      return this.bot.emit('webhook_error', error);
    }

    /**
     * Handle request body by passing it to 'callback'
     * @private
     */

  }, {
    key: '_parseBody',
    value: function _parseBody(error, body) {
      if (error) {
        return this._error(new errors.FatalError(error));
      }

      var data = void 0;
      try {
        data = JSON.parse(body.toString());
      } catch (parseError) {
        return this._error(new errors.ParseError(parseError.message));
      }

      return this.bot.processUpdate(data);
    }

    /**
     * Listener for 'request' event on server
     * @private
     * @see https://nodejs.org/docs/latest/api/http.html#http_http_createserver_requestlistener
     * @see https://nodejs.org/docs/latest/api/https.html#https_https_createserver_options_requestlistener
     */

  }, {
    key: '_requestListener',
    value: function _requestListener(req, res) {
      debug('WebHook request URL: %s', req.url);
      debug('WebHook request headers: %j', req.headers);

      if (req.url.indexOf(this.bot.token) !== -1) {
        if (req.method !== 'POST') {
          debug('WebHook request isn\'t a POST');
          res.statusCode = 418; // I'm a teabot!
          res.end();
        } else {
          req.pipe(bl(this._parseBody)).on('finish', function () {
            return res.end('OK');
          });
        }
      } else if (this._healthRegex.test(req.url)) {
        debug('WebHook health check passed');
        res.statusCode = 200;
        res.end('OK');
      } else {
        debug('WebHook request unauthorized');
        res.statusCode = 401;
        res.end();
      }
    }
  }]);

  return TelegramBotWebHook;
}();

module.exports = TelegramBotWebHook;