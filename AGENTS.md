You are an AI agent that MUST strictly follow the rules defined in AGENTS.md.

---

# AGENTS.md (RULES)

You operate in two modes:

## 1. Spec Creation Mode
If no specs exist:
- Convert the idea into:
    - requirements
    - acceptance criteria
- Create /specs/spec.md
- Create tasks.md

## 2. Execution Mode
If specs exist:
- Read /specs/spec.md
- Read tasks.md
- Implement tasks
- Create tests
- Validate acceptance criteria

---

## Core Constraints

- Do NOT implement without acceptance criteria
- Do NOT invent requirements
- Keep outputs minimal and structured
- No explanations unless requested
- Follow step-by-step output

---

## Output Order (STRICT)

1. /specs/spec.md
2. tasks.md
3. implementation
4. tests

---

# USER IDEA

Build a reactive microservice using Java 21 and Quarkus to manage waste collection.

There are two roles:
- Generator
- Waste Collector

Flow:

1. Generator creates a collection request:
    - address
    - materials
    - weight

2. System:
    - stores request in MongoDB
    - finds nearby collectors that accept materials
    - returns list

3. Generator selects a collector

4. Collector:
    - accepts → continue
    - rejects → notify generator to choose another

5. If accepted:
    - collection starts
    - both confirm completion

---

# EXTERNAL SERVICE (DO NOT REIMPLEMENT)

User service (PostgreSQL + Prisma) provides:
- users
- collectors
- materials
- addresses

Use Kafka events to:
- sync required data into MongoDB
- notify system events

---

# TECH STACK

- Java 21
- Quarkus Reactive (Mutiny)
- MongoDB
- Kafka
- Maven

---

# REQUIREMENTS

- Use clean/hexagonal architecture
- Use reactive patterns (Uni/Multi)
- Keep code minimal
- Only implement what is required

---

# INSTRUCTIONS

Start in Spec Creation Mode.

Do NOT jump to code.

Follow AGENTS.md strictly.