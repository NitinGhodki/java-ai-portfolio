# Week A — Java AI Foundations

7 days building production Java AI systems with Spring AI and LangChain4j.

## What's in here
[Day 1-7 folder links with one-line descriptions]

## Python vs Java AI — comparison table

| Feature | Python (LangChain) | Spring AI | LangChain4j |
|---|---|---|---|
| Chat call | `client.chat()` | `ChatClient.create()` | `AiServices.create()` |
| RAG | Manual chain / LCEL | `QuestionAnswerAdvisor` | `ContentRetriever` |
| Memory | Manual list | `MessageChatMemoryAdvisor` | `chatMemoryProvider` |
| Tools | `@tool` decorator | `@Bean Function<I,O>` | `@Tool` annotation |
| Structured output | Pydantic + manual parse | `BeanOutputConverter` | Return type on interface |
| Guardrails | Manual function calls | Manual | `@InputGuardrails` annotation |
| Cross-encoder rerank | `sentence-transformers` | Not built-in | Not built-in — use Cohere API |
| Local embeddings | `sentence-transformers` | API call required | `AllMiniLmL6V2EmbeddingModel` (local) |

## Cache + rate limit results
[Your actual latency numbers from cache hit/miss test]
[Your actual 429 results from rate limit test]

## What I would improve
1. Replace ConcurrentHashMap rate limit buckets with Caffeine TTL cache
2. Add Cohere rerank API for true cross-encoder reranking
3. Swap SimpleVectorStore for PgVectorStore for production persistence
4. Add Micrometer custom metrics for cache hit rate tracking
5. Implement semantic caching (embedding similarity) instead of exact-match