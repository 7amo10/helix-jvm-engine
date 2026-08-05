# Helix Enterprise Example Rules Catalog

This directory contains a catalog of enterprise business rules formatted in JSON for compilation and execution using the Helix JVM Scripting Engine CLI.

---

## Rule Catalog Index

| Rule File | Rule Name | Domain Category | Key Input Fields | Expression |
|---|---|---|---|---|
| `fraud-detection.json` | `FraudDetectionRule` | Financial Security | `amount`, `country` | `amount > 10000 && country != "US"` |
| `credit-approval.json` | `CreditApprovalRule` | Banking & Credit | `creditScore`, `debtToIncomeRatio` | `creditScore >= 700 && debtToIncomeRatio < 0.35` |
| `age-verification.json` | `AgeVerificationRule` | Identity & Compliance | `age`, `verifiedIdentity` | `age >= 21 && verifiedIdentity == true` |
| `discount-calculator.json` | `DiscountCalculatorRule` | E-Commerce Retail | `cartTotal`, `loyaltyMember` | `cartTotal >= 150.0 || loyaltyMember == true` |
| `data-validation.json` | `DataValidationRule` | User Onboarding | `emailLength`, `status` | `emailLength > 5 && (status == "ACTIVE" || status == "PENDING")` |
| `security-acl.json` | `SecurityAclRule` | Access Control | `role`, `department` | `role == "ADMIN" || (role == "MANAGER" && department == "FINANCE")` |
| `dynamic-pricing.json` | `DynamicPricingRule` | Surge & Revenue | `demandMultiplier`, `surgeActive` | `demandMultiplier > 1.5 && surgeActive == true` |
| `inventory-reorder.json` | `InventoryReorderRule` | Supply Chain | `stockCount`, `minimumThreshold`, `autoReorder` | `stockCount < minimumThreshold && autoReorder == true` |
| `sla-monitor.json` | `SlaMonitorRule` | APM Observability | `responseTimeMs`, `httpStatus` | `responseTimeMs > 500 || httpStatus == 500` |
| `tax-calculator.json` | `TaxCalculatorRule` | Accounting | `taxableIncome`, `stateTaxExempt` | `taxableIncome > 50000.0 && stateTaxExempt == false` |

---

## How to Compile & Execute Rules via CLI

### 1. Compile Rule
```bash
./scripts/start-helix.sh compile --rule examples/rules/credit-approval.json --output json
```

### 2. Execute Rule with Context
```bash
./scripts/start-helix.sh execute --rule examples/rules/credit-approval.json --context examples/rules/sample-context.json --mode async --output json
```
