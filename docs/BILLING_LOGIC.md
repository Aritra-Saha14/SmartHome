# SEMHAS — Billing Logic

## 1. Purpose
Provide a continuously updating estimated electricity cost.

## 2. User Rate
User chooses a per-unit electricity cost in ₹/kWh.
Example: ₹8.00/kWh.

The rate must be editable.

## 3. Formula
estimatedCost = consumedEnergyKwh × ratePerKwh

Examples:
- 10 kWh × ₹8 = ₹80
- 123.456 kWh × ₹8.50 = ₹1,049.376, displayed as ₹1,049.38

## 4. Running Bill
As mock consumed energy increases, estimated cost increases accordingly.

The calculation should retain precision internally and round only for presentation.

## 5. Views
Provide:
- Current billing period
- Total energy consumed
- Current estimated bill
- Rate per unit
- Daily cost
- Monthly cost
- Appliance-wise cost

## 6. Rate Change
When the user changes the rate:
- save the new mock rate
- recalculate estimated costs
- update billing UI

## 7. Disclaimer
This is an estimated bill, not an official electricity utility bill.
Do not include slabs, taxes, fixed charges, demand charges, or utility-specific tariff rules in this version.

## 8. Future
A future version may support tariff slabs and utility-specific billing, but this must not be implemented now.
