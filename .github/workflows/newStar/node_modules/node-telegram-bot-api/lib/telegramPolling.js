'use strict';

var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function (obj) { return typeof obj; } : function (obj) { return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; };

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var errors = require('./errors');
var debug = require('debug')('node-telegram-bot-api');
var deprecate = require('./utils').deprecate;
var ANOTHER_WEB_HOOK_USED = 409;

var TelegramBotPolling = function () {
  /**
   * Handles polling against the Telegram servers.
   * @param  {TelegramBot} bot
   * @see https://core.telegram.org/bots/api#getting-updates
   */
  function TelegramBotPolling(bot) {
    _classCallCheck(this, TelegramBotPolling);

    this.bot = bot;
    this.options = typeof bot.options.polling === 'boolean' ? {} : bot.options.polling;
    this.options.interval = typeof this.options.interval === 'number' ? this.options.interval : 300;
    this.options.params = _typeof(this.options.params) === 'object' ? this.options.params : {};
    this.options.params.offset = typeof this.options.params.offset === 'number' ? this.options.params.offset : 0;
    this.options.params.timeout = typeof this.options.params.timeout === 'number' ? this.options.params.timeout : 10;
    if (typeof this.options.timeout === 'number') {
      deprecate('`options.polling.timeout` is deprecated. Use `options.polling.params` instead.');
      this.options.params.timeout = this.options.timeout;
    }
    this._lastUpdate = 0;
    this._lastRequest = null;
    this._abort = false;
    this._pollingTimeout = null;
  }

  /**
   * Start polling
   * @param  {Object} [options]
   * @param  {Object} [options.restart]
   * @return {Promise}
   */


  _createClass(TelegramBotPolling, [{
    key: 'start',
    value: function start() {
      var _this = this;

      var options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

      if (this._lastRequest) {
        if (!options.restart) {
          return Promise.resolve();
        }
        return this.stop({
          cancel: true,
          reason: 'Polling restart'
        }).then(function () {
          return _this._polling();
        });
      }
      return this._polling();
    }

    /**
     * Stop polling
     * @param  {Object} [options] Options
     * @param  {Boolean} [options.cancel] Cancel current request
     * @param  {String} [options.reason] Reason for stopping polling
     * @return {Promise}
     */

  }, {
    key: 'stop',
    value: function stop() {
      var _this2 = this;

      var options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

      if (!this._lastRequest) {
        return Promise.resolve();
      }
      var lastRequest = this._lastRequest;
      this._lastRequest = null;
      clearTimeout(this._pollingTimeout);
      if (options.cancel) {
        var reason = options.reason || 'Polling stop';
        lastRequest.cancel(reason);
        return Promise.resolve();
      }
      this._abort = true;
      return lastRequest.finally(function () {
        _this2._abort = false;
      });
    }

    /**
     * Return `true` if is polling. Otherwise, `false`.
     */

  }, {
    key: 'isPolling',
    value: function isPolling() {
      return !!this._lastRequest;
    }

    /**
     * Handle error thrown during polling.
     * @private
     * @param  {Error} error
     */

  }, {
    key: '_error',
    value: function _error(error) {
      if (!this.bot.listeners('polling_error').length) {
        return console.error('error: [polling_error] %j', error); // eslint-disable-line no-console
      }
      return this.bot.emit('polling_error', error);
    }

    /**
     * Invokes polling (with recursion!)
     * @return {Promise} promise of the current request
     * @private
     */

  }, {
    key: '_polling',
    value: function _polling() {
      var _this3 = this;

      this._lastRequest = this._getUpdates().then(function (updates) {
        _this3._lastUpdate = Date.now();
        debug('polling data %j', updates);
        updates.forEach(function (update) {
          _this3.options.params.offset = update.update_id + 1;
          debug('updated offset: %s', _this3.options.params.offset);
          try {
            _this3.bot.processUpdate(update);
          } catch (err) {
            err._processing = true;
            throw err;
          }
        });
        return null;
      }).catch(function (err) {
        debug('polling error: %s', err.message);
        if (!err._processing) {
          return _this3._error(err);
        }
        delete err._processing;
        /*
         * An error occured while processing the items,
         * i.e. in `this.bot.processUpdate()` above.
         * We need to mark the already-processed items
         * to avoid fetching them again once the application
         * is restarted, or moves to next polling interval
         * (in cases where unhandled rejections do not terminate
         * the process).
         * See https://github.com/yagop/node-telegram-bot-api/issues/36#issuecomment-268532067
         */
        if (!_this3.bot.options.badRejection) {
          return _this3._error(err);
        }
        var opts = {
          offset: _this3.options.params.offset,
          limit: 1,
          timeout: 0
        };
        return _this3.bot.getUpdates(opts).then(function () {
          return _this3._error(err);
        }).catch(function (requestErr) {
          /*
           * We have been unable to handle this error.
           * We have to log this to stderr to ensure devops
           * understands that they may receive already-processed items
           * on app restart.
           * We simply can not rescue this situation, emit "error"
           * event, with the hope that the application exits.
           */
          /* eslint-disable no-console */
          var bugUrl = 'https://github.com/yagop/node-telegram-bot-api/issues/36#issuecomment-268532067';
          console.error('error: Internal handling of The Offset Infinite Loop failed');
          console.error('error: Due to error \'' + requestErr + '\'');
          console.error('error: You may receive already-processed updates on app restart');
          console.error('error: Please see ' + bugUrl + ' for more information');
          /* eslint-enable no-console */
          return _this3.bot.emit('error', new errors.FatalError(err));
        });
      }).finally(function () {
        if (_this3._abort) {
          debug('Polling is aborted!');
        } else {
          debug('setTimeout for %s miliseconds', _this3.options.interval);
          _this3._pollingTimeout = setTimeout(function () {
            return _this3._polling();
          }, _this3.options.interval);
        }
      });
      return this._lastRequest;
    }

    /**
     * Unset current webhook. Used when we detect that a webhook has been set
     * and we are trying to poll. Polling and WebHook are mutually exclusive.
     * @see https://core.telegram.org/bots/api#getting-updates
     * @private
     */

  }, {
    key: '_unsetWebHook',
    value: function _unsetWebHook() {
      debug('unsetting webhook');
      return this.bot._request('setWebHook');
    }

    /**
     * Retrieve updates
     */

  }, {
    key: '_getUpdates',
    value: function _getUpdates() {
      var _this4 = this;

      debug('polling with options: %j', this.options.params);
      return this.bot.getUpdates(this.options.params).catch(function (err) {
        if (err.response && err.response.statusCode === ANOTHER_WEB_HOOK_USED) {
          return _this4._unsetWebHook().then(function () {
            return _this4.bot.getUpdates(_this4.options.params);
          });
        }
        throw err;
      });
    }
  }]);

  return TelegramBotPolling;
}();

module.exports = TelegramBotPolling;