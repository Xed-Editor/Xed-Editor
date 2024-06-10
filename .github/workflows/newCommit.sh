echo $TOKEN
curl -X POST -H 'Content-Type: application/json' \
                          -d '{"message_thread_id": "8","chat_id": "-1002225667339_8", "text": "New <a href='"$COMMIT_URL"'>Commit</a> by <a href=\"https://github.com/'"$USER"'\">@'"$USER"'</a>\n", "parse_mode": "HTML"}' \
                          https://api.telegram.org/bot$TOKEN/sendMessage

