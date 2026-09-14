curl -X POST https://llamat.emediaworkspace.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-dummy" \
  -d '{
    "model": "Qwen3.8-27B-GGUF",
    "messages": [{"role": "user", "content": "What is 2 + 3"}]
  }'