const token = process.env.TOKEN;
const TelegramBot = require('node-telegram-bot-api');
// Replace 'YOUR_BOT_TOKEN' with your actual bot token
const bot = new TelegramBot(token, { polling: true });

// Replace 'YOUR_GROUP_CHAT_ID' with the ID of your group chat
const chatId = '-1002225667339';
const actor = process.env.GITHUB_ACTOR;
// Send a message to the group chat
bot.sendMessage(chatId, "⭐ <a href=\"http://example.com\">@"+actor+"</a> Starred <a href=\"http://example.com\">Xed-Editor</a>", { parse_mode: "HTML" });
process.exit();
