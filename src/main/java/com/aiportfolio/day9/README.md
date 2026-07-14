## 📊 LLM Performance Benchmarks

Below is a comparison between the cloud-hosted **Hugging Face API** and the local **Ollama** instance across different prompt complexities.

### ⏱️ Performance & Cost Summary

| Metric | Hugging Face (Cloud) | Ollama (Local) | Winner |
| :--- | :--- | :--- | :--- |
| **Average Latency** | 2,936 ms | 12,959 ms | 🚀 Hugging Face (4.4x Faster) |
| **Total Cost** | $0.0000238 | **$0.0000000** | 💰 Ollama (Free) |

> 💡 **Summary:** Ollama provides entirely free local generation, but Hugging Face API handles complex reasoning and text streaming significantly faster on this system.

---

### 🔍 Detailed Query Log

<details>
<summary><b>Click to expand individual prompt test results</b></summary>

#### 1. What is the capital of India? (`SIMPLE`)
* **Hugging Face:** 1,686 ms | Cost: $0.0000012
* **Ollama:** 1,285 ms | Cost: $0.00
* **Verdict:** Ollama was slightly faster on this simple factual lookup.

#### 2. Explain what a REST API is in two sentences. (`SIMPLE`)
* **Hugging Face:** 4,689 ms | Cost: $0.0000060
* **Ollama:** 6,048 ms | Cost: $0.00

#### 3. What is 15% of 8500? (`MODERATE`)
* **Hugging Face:** 2,750 ms | Cost: $0.0000075
* **Ollama:** 3,206 ms | Cost: $0.00

#### 4. List three benefits of using Java for enterprise applications. (`SIMPLE`)
* **Hugging Face:** 2,782 ms | Cost: $0.0000083
* **Ollama:** 12,792 ms | Cost: $0.00

#### 5. Explain the difference between SQL and NoSQL databases. (`MODERATE`)
* **Hugging Face:** 2,775 ms | Cost: $0.0000008 *(⚠️ Returned null/truncated response)*
* **Ollama:** 41,467 ms | Cost: $0.00 *(Completed full comprehensive breakdown)*

</details>

---

### 📝 Benchmark Data Source (Raw JSON)

If you need to programmatically parse or reproduce these benchmark stats, the raw log is stored below:

<details>
<summary>View Raw JSON Payload</summary>

```json
{
  "avgHfLatencyMs": 2936.4,
  "avgOllamaLatencyMs": 12959.6,
  "totalHfCostUsd": 0.0000238,
  "totalOllamaCostUsd": 0,
  "summary": "Hugging Face is roughly 4.4x faster on average, though Ollama operates at zero operational cost."
}
```
</details>
