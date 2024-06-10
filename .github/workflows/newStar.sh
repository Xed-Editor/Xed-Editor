curl -X POST -H 'Content-Type: application/json' \
  -d '{"chat_id": "-1002225667339_8", "text": "⭐ <a href=\"https://github.com/'"$USER"'\">@'"$USER"'</a> Starred <a href=\"http://example.com\">Xed-Editor</a>", "parse_mode": "HTML"}' \
  https://api.telegram.org/bot$TOKEN/sendMessage