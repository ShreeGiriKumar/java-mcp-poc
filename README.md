# Java MCP POC

A Spring Boot proof-of-concept demonstrating AI agent patterns using the Anthropic Claude API. The project showcases two modes of operation: a single conversational agent with chat memory, and a multi-agent orchestration system where a powerful model routes tasks to specialized sub-agents.

## Features

- **Single Agent (`/ask`)** — Conversational agent with per-session chat memory. Supports weather queries, temperature conversion, calculator, and IPL cricket scores.
- **Multi-Agent (`/multi-agent`)** — Orchestrator pattern where Claude Sonnet 4.6 routes requests to specialized Haiku-powered sub-agents (weather, math, cricket). Handles compound questions by calling multiple sub-agents in parallel.

## Tech Stack

- Java 21
- Spring Boot 4.0.5
- Anthropic Java SDK 2.22.0 (Claude Sonnet 4.6 + Haiku 4.5)

## Prerequisites

- Java 21+
- Maven
- Anthropic API key

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/ShreeGiriKumar/java-mcp-poc.git
   cd java-mcp-poc
   ```

2. Add your Anthropic API key in `src/main/resources/application.properties`:
   ```properties
   claude.api.key=your-api-key-here
   claude.base.url=https://api.anthropic.com/v1/messages
   ```

3. Build and run:
   ```bash
   mvn spring-boot:run
   ```

The server starts at `http://localhost:8080`.

## API Usage

### Single Agent — `/ask`

Conversational agent with memory scoped to a session.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `q`       | Yes      | Your question |
| `session` | No       | Session ID for chat memory (default: `default`) |

**Examples:**
```
GET http://localhost:8080/ask?q=what+is+the+weather+in+Boston+now

GET http://localhost:8080/ask?q=what+is+CSK+IPL+score

GET http://localhost:8080/ask?q=what+is+25+multiplied+by+4

GET http://localhost:8080/ask?q=what+is+the+weather+in+Boston+now&session=user123
```

---

### Multi-Agent — `/multi-agent`

Orchestrator that routes requests to specialized sub-agents. Supports compound questions.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `q`       | Yes      | Your question (can be compound) |

**Sub-agents available:**
- `weather_agent` — Weather lookups and temperature conversion
- `math_agent` — Arithmetic calculations
- `cricket_agent` — IPL match scores

**Examples:**
```
GET http://localhost:8080/multi-agent?q=what+is+the+weather+in+Boston

GET http://localhost:8080/multi-agent?q=what+is+25+multiplied+by+4

GET http://localhost:8080/multi-agent?q=what+is+CSK+score

# Compound question — calls weather_agent and math_agent in parallel
GET http://localhost:8080/multi-agent?q=what+is+the+weather+in+NYC+and+what+is+10+times+15
```

## Architecture

```
User
 │
 ├─► /ask ──► AgentService (single agent with memory)
 │              └─► Tools: WeatherTool, CalculatorTool, CricketTool, ConvertTemperatureTool
 │
 └─► /multi-agent ──► OrchestratorAgentService (Claude Sonnet 4.6)
                          └─► weather_agent (Claude Haiku 4.5)
                          │       └─► WeatherTool, ConvertTemperatureTool
                          ├─► math_agent (Claude Haiku 4.5)
                          │       └─► CalculatorTool
                          └─► cricket_agent (Claude Haiku 4.5)
                                  └─► CricketTool
```