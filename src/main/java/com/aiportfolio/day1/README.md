# 1. Health check (Actuator)
curl http://localhost:8080/actuator/health

# 2. Basic chat
curl -X POST http://localhost:8080/api/chat \
-H "Content-Type: application/json" \
-d '{"message": "What is Spring AI in one sentence?"}'

# 3. Streaming — watch tokens arrive one by one
curl -X POST http://localhost:8080/api/chat/stream \
-H "Content-Type: application/json" \
-d '{"message": "Count from 1 to 5, one per line."}' \
--no-buffer

# 4. System prompt
curl -X POST http://localhost:8080/api/chat/system \
-H "Content-Type: application/json" \
-d '{"systemPrompt": "You are a senior Java developer. Be direct and technical.", "message": "What is dependency injection?"}'

# 5. Prompt patterns (slow — 3 LLM calls)
curl http://localhost:8080/api/patterns

# 6. Token counting
curl -X POST http://localhost:8080/api/tokens/count \
-H "Content-Type: application/json" \
-d '{"text": "What is machine learning and how does it work?", "model": "mistralai/Mistral-7B-Instruct-v0.3"}'


**what is the structural difference between Spring AI's ChatClient and your Python InferenceClient wrapper?**

Spring AI’s ChatClient acts as a high-level, enterprise-focused Java abstraction with built-in features like prompt templates, memory, and output parsing. In contrast, the Python InferenceClient is primarily a direct, lightweight HTTP wrapper for Hugging Face APIs, offering lower-level, model-specific payload control without the heavy application-layer management.