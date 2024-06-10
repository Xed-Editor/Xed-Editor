'use strict';

var util = require('util');
// Native deprecation warning
exports.deprecate = function (msg) {
  return util.deprecate(function () {}, msg, 'node-telegram-bot-api')();
};