export MSG=$(git log -1 --pretty=%B)
curl -X POST -H 'Content-Type: application/json' \
                          -d '{"message_thread_id": "8","chat_id": "-1002225667339_8", "text": "New <a href='"$COMMIT_URL"'>Commit</a> by <a href=\"https://github.com/'"$USER"'\">@'"$USER"'</a>\nDiscription: '"$MSG"'", "parse_mode": "HTML"}' \
                          https://api.telegram.org/bot$TOKEN/sendMessage

