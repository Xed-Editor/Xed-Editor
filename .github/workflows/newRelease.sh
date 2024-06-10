curl -X POST -H 'Content-Type: application/json' \
  -d '{"message_thread_id": "10","chat_id": "-1002225667339_10", "text": "New Version '"$VERSION"' just Released <a href=\"'"$URL"\"'>Download Now</a>", "parse_mode": "HTML"}' \
  https://api.telegram.org/bot$TOKEN/sendMessage