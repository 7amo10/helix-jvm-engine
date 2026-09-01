---
id: rule-syntax-and-schemas
title: Rule Syntax & JSON Schemas
sidebar_position: 1
---

# Rule Syntax & JSON Schemas

Helix rules are defined as standard JSON documents containing 4 top-level attributes: `name`, `version`, `expression`, and `inputSchema`.

---

## JSON Rule Structure

```json
{
  "name": "DiscountEligibilityRule",
  "version": "1.2.0",
  "expression": "cartTotal >= 100.0 && (isMember == true || couponsApplied > 0)",
  "inputSchema": {
    "cartTotal": "double",
    "isMember": "boolean",
    "couponsApplied": "int"
  }
}
```

### Attribute Specifications

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Unique identifier for the rule. Used for caching and ClassLoader mapping. |
| `version` | `String` | Semantic version string (`major.minor.patch`). Enables versioned caching and hot reloads. |
| `expression` | `String` | Infix boolean expression evaluated at runtime against the input context. |
| `inputSchema` | `Object` | Map of variable names to their expected primitive or object Java types. |

---

## Supported Data Types

| Schema Type Name | JVM Java Type | Default Value | Example Literals |
|---|---|---|---|
| `"int"` | `int` / `java.lang.Integer` | `0` | `42`, `-10`, `1000` |
| `"long"` | `long` / `java.lang.Long` | `0L` | `5000000000L` |
| `"double"` | `double` / `java.lang.Double` | `0.0` | `3.14159`, `100.0` |
| `"boolean"` | `boolean` / `java.lang.Boolean` | `false` | `true`, `false` |
| `"String"` | `java.lang.String` | `null` | `"US"`, `"ACTIVE"`, `"GOLD"` |

---

## Supported Operators

### Relational Operators
- Greater than: `>`, `>=`
- Less than: `<`, `<=`
- Equality: `==`, `!=`

### Logical Operators
- Logical AND: `&&`
- Logical OR: `||`
- Logical NOT: `!`
- Parentheses for grouping: `( )`

### Arithmetic Operators
- Addition & Subtraction: `+`, `-`
- Multiplication & Division: `*`, `/`
- Modulo: `%`

---

## Context Evaluation Data Format

When evaluating a rule, provide matching key-value pairs matching the `inputSchema`:

```json title="examples/rules/context.json"
{
  "cartTotal": 125.50,
  "isMember": true,
  "couponsApplied": 1
}
```
