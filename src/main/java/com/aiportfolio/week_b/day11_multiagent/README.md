## 📊 Multi-Agent Execution Metrics

This section documents the performance benchmarks and trace logs for the multi-agent system execution pipeline.

### ⏱️ System Throughput Summary
* **Total Wall-Clock Time:** `23,146 ms` (23.14 seconds)
* **Average Query Latency:** `19,673 ms`
* **Total Parallel Traces:** `2`
* **Execution Mode:** `Asynchronous / Non-Blocking Concurrent`

---

### 📑 Pipeline Execution Traces

#### 🔹 Trace 1: Plan Feature Comparison
* **Total Path Latency:** `23,145 ms`
* **Final Review State:** ✅ **APPROVED**

```text
[Pipeline Process Logs]
├── 🔍 Researcher (RAG) : 1,414 chars extracted in 13,869 ms
├── ✍️ Writer (rev1)    :   903 chars draft in  5,283 ms
└── ⚖️ Critic            : APPROVED status generated in  3,985 ms
```

<details>
<summary><b>📄 Click to expand generated final output</b></summary>

The Starter plan is priced at 999 rupees per month and includes 100 AI queries per day, with a 14-day free trial available that does not require a credit card. While the plan can be cancelled at any time, it is important to note that monthly subscriptions are not eligible for partial refunds. Technical specifications for this tier include a rate limit of 10 requests per second, and customer support is provided exclusively via email with a 48-hour response time. When compared to higher-tier options, the Starter plan is the most budget-friendly option, sitting below the Professional plan at 2999 rupees per month and the custom-priced Enterprise plan. However, it offers fewer features, such as a daily query limit rather than the unlimited access found in the Professional plan, slower support response times compared to the live chat or dedicated engineer options, and a lower request rate limit.
</details>

---

#### 🔹 Trace 2: Cancellation Policy Breakdown
* **Total Path Latency:** `16,201 ms`
* **Final Review State:** ✅ **APPROVED**

```text
[Pipeline Process Logs]
├── 🔍 Researcher (RAG) : 922 chars extracted in 8,108 ms
├── ✍️ Writer (rev1)    : 506 chars draft in 5,388 ms
└── ⚖️ Critic            : APPROVED status generated in 2,693 ms
```

<details>
<summary><b>📄 Click to expand generated final output</b></summary>

Regarding the refund policy for monthly plans, customers have the flexibility to cancel their subscription at any time; however, these plans are not eligible for partial refunds. While a 14-day free trial is available for all plans, no reimbursement is provided for the remaining portion of a billing cycle once a cancellation is processed. This policy stands in contrast to annual plans, which offer greater refund flexibility by allowing users to receive a refund within 30 days of their initial payment.
</details>


### 🏎️ Parallel Performance & Efficiency Analysis

When processing a batch of queries, executing tasks concurrently significantly boosts throughput compared to old-school sequential loops.

#### 📈 Speedup Factor Calculation
* **Sequential Processing Time ($T_{seq}$):** `45s`
* **Parallel Processing Time ($T_{par}$):** `17s`
* **Speedup Factor ($S$):**

$$S = \frac{T_{seq}}{T_{par}} = \frac{45}{17} \approx \mathbf{2.65\text{x}}$$

By executing the batch in parallel, the processing pipeline achieved a **2.65x speedup**, slashing total waiting time by roughly **62.2%**.

---

### 🛑 What Limits Further Improvement?

In a perfect, theoretical world with 3 tasks, we would expect a 3x speedup ($45\text{s} / 3 = 15\text{s}$). The system falls short of a perfect 3x speedup, and cannot be improved indefinitely, due to four architectural bottlenecks:

#### 1. Amdahl's Law (Non-Parallelizable Fractions)
Every multi-agent workflow contains strictly sequential steps that cannot be run at the same time. Tasks like reading the initial batch file, setting up thread tasks, aggregating the final JSON results, and executing internal router steps must happen one after another on a single core.

#### 2. LLM Provider API Throttling & Network Latency
Because our agents send API requests to downstream LLM endpoints, processing everything in parallel creates massive concurrent spikes. Large Language Model providers actively throttle these bursts via rate-limiting queues (RPM/TPM limits), forcing our parallel request threads to wait in line.

#### 3. Shared Resource Contention (Vector Store Locks)
When multiple agent threads wake up at the exact same millisecond, they all scramble to read from the same `InMemoryEmbeddingStore` or data matrix. Because memory access loops use thread-safe data structures, threads block each other momentarily while waiting for memory locks to release.

#### 4. Thread Lifecycle and Context-Switching Overhead
Spawning, managing, and cleaning up virtual worker threads in the Java Virtual Machine (JVM) isn't completely free. If the system wastes too much processing power frequently swapping CPU focus between worker threads (context-switching), the management overhead cancels out the raw speed gains.
