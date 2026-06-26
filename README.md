# Bank Agent - Java

Spring Boot 3 / Java 17 banking AI agent that mirrors a Python banking advisor flow using Gemini on Vertex AI, tool sequencing, database-backed customer insights, and tool-call observability.

## Run locally

```bash
./gradlew bootRun
```

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---:|---|
| `GOOGLE_CLOUD_PROJECT` | For Vertex AI / BigQuery | empty | GCP project id |
| `GOOGLE_CLOUD_LOCATION` | No | `us-central1` | Vertex AI region |
| `GEMINI_MODEL` | No | `gemini-2.5-flash` | Gemini model name |
| `BQ_DATASET` | No | empty | When set, query BigQuery; otherwise SQLite `bank_data.db` |
| `ESTIMATED_SAVINGS_RATE` | No | `0.035` | Estimated savings APY |
| `ESTIMATED_ISA_RATE` | No | `0.045` | Estimated ISA APY |
| `PORT` | No | `8080` | HTTP port |

## Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/chat` | Chat with the banking agent |
| POST | `/api/sessions` | Create a new session id |
| GET | `/obs/summary` | Aggregate tool observability summary |
| GET | `/obs/tools` | Per-tool stats |
| GET | `/obs/tools/sessions` | Sessions with recorded tool activity |
| GET | `/obs/tools/session/{id}` | Ordered tool calls for a session |
| GET | `/obs/coverage` | Registered-vs-called tool coverage |
| POST | `/obs/reset` | Clear observability state |
