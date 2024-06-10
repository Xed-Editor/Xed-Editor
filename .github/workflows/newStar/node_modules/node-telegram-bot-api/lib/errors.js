'use strict';

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

exports.BaseError = function (_Error) {
  _inherits(BaseError, _Error);

  /**
   * @class BaseError
   * @constructor
   * @private
   * @param  {String} code Error code
   * @param  {String} message Error message
   */
  function BaseError(code, message) {
    _classCallCheck(this, BaseError);

    var _this = _possibleConstructorReturn(this, (BaseError.__proto__ || Object.getPrototypeOf(BaseError)).call(this, code + ': ' + message));

    _this.code = code;
    return _this;
  }

  _createClass(BaseError, [{
    key: 'toJSON',
    value: function toJSON() {
      return {
        code: this.code,
        message: this.message
      };
    }
  }]);

  return BaseError;
}(Error);

exports.FatalError = function (_exports$BaseError) {
  _inherits(FatalError, _exports$BaseError);

  /**
   * Fatal Error. Error code is `"EFATAL"`.
   * @class FatalError
   * @constructor
   * @param  {String|Error} data Error object or message
   */
  function FatalError(data) {
    _classCallCheck(this, FatalError);

    var error = typeof data === 'string' ? null : data;
    var message = error ? error.message : data;

    var _this2 = _possibleConstructorReturn(this, (FatalError.__proto__ || Object.getPrototypeOf(FatalError)).call(this, 'EFATAL', message));

    if (error) _this2.stack = error.stack;
    return _this2;
  }

  return FatalError;
}(exports.BaseError);

exports.ParseError = function (_exports$BaseError2) {
  _inherits(ParseError, _exports$BaseError2);

  /**
   * Error during parsing. Error code is `"EPARSE"`.
   * @class ParseError
   * @constructor
   * @param  {String} message Error message
   * @param  {http.IncomingMessage} response Server response
   */
  function ParseError(message, response) {
    _classCallCheck(this, ParseError);

    var _this3 = _possibleConstructorReturn(this, (ParseError.__proto__ || Object.getPrototypeOf(ParseError)).call(this, 'EPARSE', message));

    _this3.response = response;
    return _this3;
  }

  return ParseError;
}(exports.BaseError);

exports.TelegramError = function (_exports$BaseError3) {
  _inherits(TelegramError, _exports$BaseError3);

  /**
   * Error returned from Telegram. Error code is `"ETELEGRAM"`.
   * @class TelegramError
   * @constructor
   * @param  {String} message Error message
   * @param  {http.IncomingMessage} response Server response
   */
  function TelegramError(message, response) {
    _classCallCheck(this, TelegramError);

    var _this4 = _possibleConstructorReturn(this, (TelegramError.__proto__ || Object.getPrototypeOf(TelegramError)).call(this, 'ETELEGRAM', message));

    _this4.response = response;
    return _this4;
  }

  return TelegramError;
}(exports.BaseError);