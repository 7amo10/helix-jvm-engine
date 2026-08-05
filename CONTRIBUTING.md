# Contributing to Helix JVM Scripting Engine & Profiler

Thank you for your interest in contributing to Helix! We welcome pull requests, bug reports, feature suggestions, and documentation improvements.

---

## Code of Conduct

Please maintain a professional, courteous, and constructive environment in all interactions across issues, pull requests, and discussions.

---

## Development Workflow

### 1. Prerequisites
- **Java Development Kit (JDK):** Version 17 or higher.
- **Build System:** Apache Maven 3.8+.
- **Version Control:** Git.

### 2. Fork and Branching Model
- Fork the official repository `7amo10/helix-jvm-engine`.
- Create a feature or bugfix branch off `develop`:
  ```bash
  git checkout develop
  git pull origin develop
  git checkout -b task/X.Y-feature-name
  ```

### 3. Build & Verification Checklist
Before submitting a pull request, ensure all Maven tests pass and test coverage remains above 80%:

```bash
mvn clean test
```

Verify that all existing unit tests in `engine-api`, `engine-core`, `engine-profiler`, `engine-agent`, and `engine-experiments` execute cleanly.

---

## Commit Guidelines

- Write clear, concise commit messages in Conventional Commits format:
  - `feat(core): add BytecodeOptimizer pass`
  - `fix(profiler): correct JFR event duration calculation`
  - `docs(readme): update performance benchmarks table`
  - `test(agent): add coverage test for RuleClassTransformer`
- **Do not include emojis** in commit titles, pull request descriptions, or code documentation.

---

## Pull Request Submission

1. Push your feature branch to your fork.
2. Open a Pull Request targeting the `develop` branch.
3. Complete the pull request template with a summary of changes, test evidence, and linked issues.
