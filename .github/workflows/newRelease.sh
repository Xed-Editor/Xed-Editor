curl -X POST -H 'Content-Type: application/json' \
  -d '{"message_thread_id": "10","chat_id": "-1002225667339_10", "text": "New Version $VERSION just Released Download Here $URL"' \
  https://api.telegram.org/bot$TOKEN/sendMessage