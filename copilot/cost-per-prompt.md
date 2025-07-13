### Short answer

It *can* work, but you’ll avoid a lot of headaches if you store **token counts + model/provider metadata** in each prompt and treat *dollar cost* as a **derived view**, not a fixed field that’s baked into the prompt forever.

---

## 1  Where it helps

| Benefit                      | Why it’s nice                                                                                                  |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------- |
| **Instant budget snapshots** | A plain `sum(:cost)` over the prompt tree tells you what you’ve spent so far.                                  |
| **Immutable provenance**     | Because prompt files in the PDB never change, a cost recorded at creation time can’t be silently retro-edited. |
| **Roll-up is cheap**         | Each prompt already stores a list of ancestor P-IDs; a DFS can aggregate numbers without extra queries.        |
| **No external lookup**       | Dashboards don’t need to know today’s OpenAI/Mistral prices just to show historical spend.                     |

---

## 2  Hidden traps & mitigation

| Pitfall                                   | Why it bites                                                                                                                                          | Mitigation / alternative                                                                                                                                                        |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Price tables change**                   | Today’s “\$0.01 / 1K tokens” may be half that next quarter. An old prompt’s stored  *“\$1.23”* loses meaning without a timestamp.                     | Store **token-in / token-out counts + model ID + creation timestamp**. Let the reporting layer multiply by whatever per-token table is correct for that date/provider/currency. |
| **Multi-currency confusion**              | Some providers (e.g. Ollama + local GPU) have *zero* marginal cost, others charge in EUR, etc.                                                        | Keep `:currency` and interpret “local” models as `0 cost` but still capture token counts so you can show energy/time overhead later.                                            |
| **Double-counting shared ancestors**      | Two children that derive from the same parent will each include the parent’s cost in their *local* roll-up. A naïve “sum all leaves” inflates totals. | Roll up on a **DAG traversal with a visited set**, or cache an `:cumulative-token-in/out` in each prompt and compute it once.                                                   |
| **Hand-written or cut-and-paste prompts** | They cost *labor* but `$0` in API calls. Mixing “labor cost” and “API cost” in the same numeric field is messy.                                       | Separate fields: `:api-tokens`, `:api-cost-usd`, and maybe a future `:human-minutes` if you want to monetise editing time.                                                      |
| **Price precision vs. rounding on write** | Storing `$0.0035` as `$0.00` loses traceability.                                                                                                      | Always store the **smallest unit (tokens)** and derive currency with arbitrary-precision math when displaying.                                                                  |

---

## 3  Recommended data model

```edn
{:p-id        "P42"
 :created-at  #inst "2025-07-12T13:45:00.000-04:00"
 :model       "openai/gpt-4o-mini"
 :token-in    1574          ;; prompt + system
 :token-out   211           ;; completion
 :provider    :openai
 ;; optional pre-computed helper fields
 :ancestors   ["P11" "P7"]
 ;; **do not store :usd-cost here**
}
```

*Reporters* (CLI, web dashboards, etc.) calculate:

```clj
(-> tokens (* price-table provider model date) (/ 1000M)) ; USD
```

and can cache the result in an OLAP table if you need speed.

---

## 4  Roll-up recipe

1. **Topological walk** of the prompt DAG.
2. At each node compute cumulative `token-in/out` by summing its own plus the maxima of each ancestor path (visited-set guards against duplication).
3. Store those cumulative counts in a separate materialised-view table or in the prompt’s *derived* metadata section so you never re-compute unless the graph changes.

---

### Bottom line

*Store immutable facts (token counts, model, timestamp) **inside** the prompt; compute money outside.*
That approach survives pricing changes, supports multi-provider accounting, and still lets you roll everything up from the leaves to the root with a single query.
