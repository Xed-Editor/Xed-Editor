# Define the JSON payload separately for clarity
payload='{
    "message_thread_id": "8",
    "chat_id": "-1002225667339_8",
    "text": "New <a href=\"'$COMMIT_URL'\">Commit</a> by <a href=\"https://github.com/'$USER'\">@'$USER'</a>\nDescription: '$MSG'",
    "parse_mode": "HTML"
}'

# Use the payload variable in the curl command
curl -X POST \
     -H 'Content-Type: application/json' \
     -d "$payload" \
     "https://api.telegram.org/bot$TOKEN/sendMessage"


