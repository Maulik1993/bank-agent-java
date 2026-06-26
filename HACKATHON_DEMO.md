# 🏦 Intelligent Banking AI Agent — Hackathon Demo

> **Team:** EDB Hackathon | **Project:** AI-Powered Personalised Banking Advisor  
> **Repos:** [Python/ADK](https://github.com/Maulik1993/hackathon_test) · [Java/Spring Boot](https://github.com/Maulik1993/bank-agent-java)

---

## 1. Executive Summary

A production-grade **AI banking agent** that analyses a customer's real financial data end-to-end and generates a deeply personalised savings recommendation — including expense reduction advice, product allocation, projected returns, and a concrete action plan — in under 30 seconds.

Built in **two technology stacks** (Python + Google ADK, and Java + Spring Boot), both powered by **Vertex AI Gemini 2.5 Flash** and backed by **Google BigQuery**.

---

## 2. The Problem We Solve

| Pain Point | Traditional Banking | Our AI Agent |
|---|---|---|
| Generic advice | "We recommend savings accounts" | Personalised to exact balance, spending, age |
| Manual analysis | Analyst reviews statements manually | 9 tools run automatically in sequence |
| Static products | Pre-defined brochures | Dynamic product matching + rate comparison |
| No projections | Vague "grow your money" messaging | Exact £ interest, year-end balance forecast |
| Siloed data | Customer calls multiple departments | Single query → full financial picture |

---

## 3. End-to-End System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        USER / BROWSER                               │
│              "I want to save money. My ID is C002"                  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ HTTP POST /api/chat
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   SPRING BOOT APPLICATION                            │
│                   (Java 17 · Port 8080)                             │
│                                                                     │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │  ChatController  │───▶│ BankAgentService │───▶│  AgentConfig  │  │
│  │  POST /api/chat  │    │  (Orchestrator)  │    │ (Gemini Bean) │  │
│  └─────────────────┘    └────────┬─────────┘    └───────────────┘  │
│                                  │                                  │
│            ┌─────────────────────┼──────────────────────┐          │
│            ▼                     ▼                      ▼          │
│   ┌────────────────┐  ┌────────────────────┐  ┌──────────────────┐ │
│   │  Tool Layer    │  │  DatabaseService   │  │ ObservabilityStore│ │
│   │  (9 Tools)     │  │  BigQuery/SQLite   │  │  Tool Coverage   │ │
│   └───────┬────────┘  └────────┬───────────┘  └──────────────────┘ │
└───────────┼────────────────────┼─────────────────────────────────────┘
            │                    │
            ▼                    ▼
┌───────────────────┐  ┌─────────────────────────────────────────────┐
│  VERTEX AI        │  │          GOOGLE BIGQUERY                    │
│  Gemini 2.5 Flash │  │  ltc-ipnihack-prj-16.BQ_DATASET             │
│  (LLM Synthesis)  │  │  ├── customers   (demographics)             │
│  us-central1      │  │  ├── accounts    (balances, product types)  │
│                   │  │  └── transactions (credits, debits)         │
└───────────────────┘  └─────────────────────────────────────────────┘
```

---

## 4. What Happens When a Request Comes In

```
Customer asks: "I want to save money. My customer ID is C002"
                              │
                              ▼
              ┌───────────────────────────────┐
              │  Step 0: Extract Customer ID  │
              │  Regex: \bC\d{3,4}\b → "C002" │
              └───────────────┬───────────────┘
                              │
        ┌─────────────────────▼──────────────────────┐
        │         JAVA ORCHESTRATOR (BankAgentService)│
        │         9 tools called in sequence          │
        └─────────────────────────────────────────────┘
                              │
    ┌─────────────────────────┼─────────────────────────┐
    │         TOOL PIPELINE   │    (~20 seconds total)  │
    │                         │                         │
    │  1. customerIdSearch ───┤ Verify identity         │
    │                         │ → "Customer verified:   │
    │                         │   C002, Bob Hargreaves" │
    │                         │                         │
    │  2. getCustomerProfile ─┤ Demographics + accounts │
    │                         │ → name, age, gender,    │
    │                         │   account types, total  │
    │                         │   balance: £6,350.20    │
    │                         │                         │
    │  3. analyzeSpending ────┤ Transaction breakdown   │
    │                         │ → credits/debits, avg   │
    │                         │   transaction, ratio    │
    │                         │                         │
    │  4. analyzeSavings ─────┤ Savings mix analysis    │
    │                         │ → savings %, liquid %,  │
    │                         │   total assets          │
    │                         │                         │
    │  5. recommendProduct ───┤ Structured JSON data    │
    │                         │ → balances, cash flow,  │
    │                         │   top expenses, income, │
    │                         │   available products    │
    │                         │   with interest rates   │
    │                         │                         │
    │  6. financialHistory ───┤ Net cash flow picture   │
    │                         │ → inflows, outflows,    │
    │                         │   net position          │
    │                         │                         │
    │  7. matchingProducts ───┤ Rank by liquidity       │
    │                         │ → Current(rank1),       │
    │                         │   Savings(rank2),       │
    │                         │   ISA(rank3)            │
    │                         │                         │
    │  8. optimalAllocation ──┤ Risk-based split        │
    │                         │ → moderate/weekly →     │
    │                         │   35% current, 45%      │
    │                         │   savings, 20% ISA      │
    │                         │                         │
    │  9. personalizeRec ─────┤ Format final message    │
    │                         │ → "Hi Bob, here is..."  │
    └─────────────────────────┘                         │
                              │                         │
                              ▼                         │
              ┌───────────────────────────────┐         │
              │  VERTEX AI GEMINI 2.5 FLASH   │         │
              │  One LLM call with all data   │◀────────┘
              │  Context: ~3,000 tokens       │
              │  Output: ~2,500 tokens        │
              └───────────────┬───────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │     6-SECTION RESPONSE        │
              │  1. Financial Snapshot        │
              │  2. Transactional Activity    │
              │  3. Expense Reduction Tips    │
              │  4. Product Allocation        │
              │  5. Projected Returns         │
              │  6. Action Plan               │
              └───────────────────────────────┘
```

---

## 5. Tool Selection Logic — How Orchestrator Decides

The orchestrator uses **input-driven tool selection**:

```
                    ┌─────────────────────────────┐
                    │     Incoming User Message    │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  Does message contain        │
                    │  customer ID (C\d{3,4})?     │
                    └──────────────┬──────────────┘
                          │                │
                        YES               NO
                          │                │
                          ▼                ▼
              ┌───────────────────┐  ┌──────────────────┐
              │ Run 9-Tool        │  │ Direct LLM call  │
              │ Financial         │  │ (general banking │
              │ Pipeline          │  │  FAQ / help)     │
              └─────────┬─────────┘  └──────────────────┘
                        │
            ┌───────────┴───────────┐
            │  Allocation Logic     │
            │  (Tool 8 Input)       │
            │                       │
            │  accessibilityReq     │
            │  ├─ "daily/instant"   │
            │  │   → 50/35/15 split │
            │  ├─ "weekly"          │
            │  │   → 35/45/20 split │
            │  └─ "monthly"         │
            │      → 20/50/30 split │
            │                       │
            │  riskTolerance        │
            │  ├─ "conservative"    │
            │  │   → +5% current   │
            │  └─ "aggressive"      │
            │      → +5% ISA       │
            └───────────────────────┘
```

---

## 6. Technology Stack

### Java Stack (Primary Demo)

| Layer | Technology | Purpose |
|---|---|---|
| **Runtime** | Java 17, Spring Boot 3.3.11 | Application server |
| **LLM Framework** | LangChain4j 0.36.2 | Vertex AI integration |
| **LLM Model** | Vertex AI Gemini 2.5 Flash | Reasoning & synthesis |
| **Data** | Google BigQuery SDK 2.38.2 | Real customer data |
| **Fallback DB** | SQLite (sqlite-jdbc 3.45) | Local development |
| **Observability** | Spring AOP + custom store | Tool call tracking |
| **Build** | Gradle 9.5.1 | Dependency management |
| **API** | Spring MVC REST | Chat + observability endpoints |

### Python Stack (Original Prototype)

| Layer | Technology | Purpose |
|---|---|---|
| **Runtime** | Python 3.14, FastAPI | Application server |
| **Agent Framework** | Google ADK (Agent Development Kit) | LLM agent loop |
| **LLM Model** | Vertex AI Gemini 2.5 Flash | Reasoning |
| **Data** | BigQuery Python client | Real customer data |
| **Observability** | Custom `@traced_tool` decorator | Tool call tracking |

---

## 7. Role of Vertex AI

```
┌─────────────────────────────────────────────────────────────────────┐
│                      VERTEX AI — What It Does                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  MODEL: gemini-2.5-flash  |  REGION: us-central1                   │
│  INPUT: ~3,000 tokens     |  OUTPUT: ~2,500 tokens                  │
│  LATENCY: ~5–8 seconds    |  COST: ~$0.001 per query               │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  INPUT CONTEXT (what we send to Gemini)                     │   │
│  │                                                             │   │
│  │  • System instruction (expert banking advisor role)         │   │
│  │  • User's original question                                 │   │
│  │  • Tool 1 result: identity verified                         │   │
│  │  • Tool 2 result: name, age, account types, balance         │   │
│  │  • Tool 3 result: spending breakdown, credit/debit ratio    │   │
│  │  • Tool 4 result: savings mix percentage                    │   │
│  │  • Tool 5 result: JSON with rates, cash flow, top expenses  │   │
│  │  • Tool 6 result: inflows, outflows, net cash flow          │   │
│  │  • Tool 7 result: products ranked by liquidity              │   │
│  │  • Tool 8 result: optimal allocation percentages            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  WHAT GEMINI DOES (Reasoning)                               │   │
│  │                                                             │   │
│  │  ✓ Cross-references all 8 data sources                      │   │
│  │  ✓ Identifies top discretionary expenses to cut             │   │
│  │  ✓ Computes monthly surplus and annual projections          │   │
│  │  ✓ Compares ISA vs Savings Account vs Current Account       │   │
│  │  ✓ Weights advice by customer age (52 → tax efficiency)     │   │
│  │  ✓ Writes personalised narrative in natural language        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  OUTPUT: Complete 6-section financial report                │   │
│  │  (£ amounts, %, projections — all from real data)           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  WHY VERTEX AI vs others?                                          │
│  • Native GCP auth (ADC) — no API keys needed in Cloud Shell       │
│  • Gemini 2.5 Flash: best quality/cost for structured reasoning    │
│  • Tight BigQuery integration in same GCP project                  │
│  • Enterprise SLA, data residency, audit logging                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Observability — Tool Coverage Dashboard

Every tool call is tracked automatically via **AOP (Aspect-Oriented Programming)**:

```
GET /obs/coverage
{
  "registeredCount": 10,
  "calledCount": 9,
  "coveragePct": 90.0,
  "called": ["customerIdSearch", "getCustomerProfile", "analyzeSpendingBehavior", ...],
  "missing": ["customerDatabaseSearch"]
}

GET /obs/tools/session/{session-id}
{
  "sessionId": "abc-123",
  "callCount": 9,
  "calls": [
    { "order": 1, "tool": "customerIdSearch",   "duration": "2341ms", "success": true },
    { "order": 2, "tool": "getCustomerProfile",  "duration": "2100ms", "success": true },
    { "order": 3, "tool": "analyzeSpending",     "duration": "185ms",  "success": true },
    ...
  ]
}
```

---

## 9. Sample Input → Output

### Input
```
POST /api/chat
{
  "sessionId": "demo-session-001",
  "message": "I want to save money. My customer ID is C002."
}
```

### Output (condensed)
```
Hi Bob Hargreaves (C002),

1. FINANCIAL SNAPSHOT
   Total balance: £6,350.20 | Current: £850.20 (13.4%) | ISA: £5,500.00 (86.6%)
   Monthly outgoings: £182.74 → Current account covers 4.6 months of spending ✅

2. TRANSACTIONAL ACTIVITY
   Monthly income:  £2,800.00 (Payroll - City Council)
   Monthly spend:   £182.74
   Monthly surplus: £2,617.26 ← Exceptional saving capacity

3. EXPENSE REDUCTION
   • Online Retailer £32.99 → cut 50% = save £16.50/month (£198/year)
   • Coffee Shop £4.75 → cut 50% = save £2.38/month (£28.56/year)
   Total potential extra savings: £18.88/month

4. RECOMMENDED ALLOCATION
   • Current Account (0%):  Keep £850.20 — covers emergencies
   • Cash ISA (4.5% tax-free): Deposit £2,636.14/month — priority
   • Savings Account (3.5%): No new deposits needed

5. PROJECTED ANNUAL RETURNS
   New deposits this year:    £31,633.68
   Interest on existing ISA:  £247.50
   Interest on new deposits:  £711.76
   Total interest earned:     £959.26
   Year-end total balance:    £38,943.14

6. ACTION PLAN
   ✅ Set up standing order: £2,636/month → ISA (day after payroll)
   ✅ Review "Online Retailer" subscription spend
   ✅ Use ISA allowance before April tax year end
```

---

## 10. Live Demo Flow

```
DEMO SCRIPT (10 minutes)

1. [0:00] Open browser → http://localhost:8080
          Show the chat UI

2. [0:30] Type: "I want to save money. My customer ID is C002"
          Click Send → wait ~25 seconds

3. [1:00] Show the 6-section response in UI
          Point out: real £ figures, real transaction data, personalised advice

4. [2:00] Open new tab → /obs/tools/session/{session-id}
          Show all 9 tools called, each with duration and success status

5. [3:00] Open /obs/coverage
          Show 90% tool coverage — only customerDatabaseSearch unused

6. [4:00] Cloud Shell terminal — scroll through DEBUG logs:
          - Each [TOOL] line with ms timing
          - LLM PROMPT (3,000 chars of structured data)
          - LLM RESPONSE (2,500 chars of narrative)

7. [5:00] Show architecture diagram (this doc)
          Explain: Java orchestrator → 9 BigQuery calls → 1 Gemini call → response

8. [6:00] Try different customer: C005 (Evelyn, younger, different profile)
          Show how response changes: different allocation, different projections

9. [8:00] Show Python ADK version in Cloud Shell:
          adk web → same agent, different runtime (Google ADK framework)

10. [9:30] Q&A
```

---

## 11. Key Innovation Points

| Innovation | Details |
|---|---|
| **Dual-stack delivery** | Same business logic in Python (ADK) and Java (Spring Boot) |
| **Real data, not mocks** | Live BigQuery queries against real customer transaction history |
| **Deterministic orchestration** | Java calls 9 tools in guaranteed order, then 1 LLM call — no flaky tool-calling loops |
| **Intelligent allocation** | Algorithm adapts split (Current/Savings/ISA) to risk tolerance + accessibility needs |
| **Observability-first** | Every tool call tracked with duration, success rate, session coverage |
| **Production-grade fallback** | BigQuery fails → SQLite fallback, zero downtime |
| **No hardcoded advice** | All figures come from real data; LLM only synthesises narrative |

---

## 12. Architecture Decision: Why Java Orchestrates Tools Directly

```
OPTION A: LLM-driven tool calling (tried, rejected)
  User ──▶ LLM ──▶ "call tool X" ──▶ Tool X ──▶ LLM ──▶ ...loop...
  Problem: Vertex AI Gemini drops tool-call loop in LangChain4j 0.36.2
           → Empty responses, unpredictable tool order

OPTION B: Java orchestrates tools (chosen ✅)
  User ──▶ Java calls tools 1-9 in sequence ──▶ Build context ──▶ 1 LLM call
  Benefit: Reliable, deterministic, fast, easy to observe, easy to extend
```

---

## 13. Endpoints Reference

| Endpoint | Method | Description |
|---|---|---|
| `POST /api/chat` | REST | Main chat endpoint |
| `POST /api/sessions` | REST | Create new session |
| `GET /obs/summary` | REST | Overall tool statistics |
| `GET /obs/tools` | REST | Per-tool call counts + success rates |
| `GET /obs/tools/sessions` | REST | All sessions with tool usage |
| `GET /obs/tools/session/{id}` | REST | Tool calls for specific session |
| `GET /obs/coverage` | REST | Tool coverage % |
| `POST /obs/reset` | REST | Reset observability store |

---

## 14. What's Next (Roadmap)

- [ ] **Streaming responses** — show LLM output token-by-token in UI
- [ ] **Multi-customer comparison** — "Compare C002 and C005 savings potential"
- [ ] **Proactive alerts** — "Customer C002 has not moved surplus to ISA this month"
- [ ] **Voice interface** — integrate Google Cloud Speech-to-Text
- [ ] **Cloud Run deployment** — containerise and deploy via `Dockerfile`
- [ ] **Persistent observability** — write tool calls to BigQuery for long-term analytics
- [ ] **A/B testing** — compare recommendation quality across model versions

---

*Built with ❤️ for EDB Hackathon | Powered by Google Cloud Vertex AI + BigQuery*
