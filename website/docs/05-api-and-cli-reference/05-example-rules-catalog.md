---
id: example-rules-catalog
title: Enterprise Example Rules Catalog
sidebar_position: 5
---

# Enterprise Example Rules Catalog

Helix includes a catalog of production JSON business rules covering fraud detection, banking, compliance, and retail pricing.

---

## Catalog Index

| Rule File | Rule Name | Business Domain | Input Schema | Expression |
|---|---|---|---|---|
| `fraud-detection.json` | `FraudDetectionRule` | Financial Security | `amount: int`, `country: String` | `amount > 10000 && country != "US"` |
| `credit-approval.json` | `CreditApprovalRule` | Banking & Credit | `creditScore: int`, `debtToIncomeRatio: double` | `creditScore >= 700 && debtToIncomeRatio < 0.35` |
| `age-verification.json` | `AgeVerificationRule` | Identity & Compliance | `age: int`, `verifiedIdentity: boolean` | `age >= 21 && verifiedIdentity == true` |
| `discount-calculator.json` | `DiscountCalculatorRule` | E-Commerce Retail | `cartTotal: double`, `loyaltyMember: boolean` | `cartTotal >= 150.0 || loyaltyMember == true` |
| `data-validation.json` | `DataValidationRule` | User Onboarding | `emailLength: int`, `status: String` | `emailLength > 5 && (status == "ACTIVE" || status == "PENDING")` |
| `security-acl.json` | `SecurityAclRule` | Access Control | `role: String`, `department: String` | `role == "ADMIN" || (role == "MANAGER" && department == "FINANCE")` |
| `dynamic-pricing.json` | `DynamicPricingRule` | Surge & Revenue | `demandMultiplier: double`, `surgeActive: boolean` | `demandMultiplier > 1.5 && surgeActive == true` |
| `inventory-reorder.json` | `InventoryReorderRule` | Supply Chain | `stockCount: int`, `minimumThreshold: int`, `autoReorder: boolean` | `stockCount < minimumThreshold && autoReorder == true` |
| `sla-monitor.json` | `SlaMonitorRule` | APM Observability | `responseTimeMs: long`, `httpStatus: int` | `responseTimeMs > 500 || httpStatus == 500` |
| `tax-calculator.json` | `TaxCalculatorRule` | Accounting | `taxableIncome: double`, `stateTaxExempt: boolean` | `taxableIncome > 50000.0 && stateTaxExempt == false` |

---

## Example Rule Files

### Credit Approval Rule
```json title="examples/rules/credit-approval.json"
{
    "name": "CreditApprovalRule",
    "version": "1.0.0",
    "expression": "creditScore >= 700 && debtToIncomeRatio < 0.35",
    "inputSchema": {
        "creditScore": "int",
        "debtToIncomeRatio": "double"
    }
}
```

### Security Access Control Rule
```json title="examples/rules/security-acl.json"
{
    "name": "SecurityAclRule",
    "version": "1.0.0",
    "expression": "role == \"ADMIN\" || (role == \"MANAGER\" && department == \"FINANCE\")",
    "inputSchema": {
        "role": "String",
        "department": "String"
    }
}
```
