# Risk, Margin and Settlement

This document describes how the application implements the risk and margin
engine and settlement. It covers the business reasoning, the rules and
formulas, the transaction guarantees, the API and the known simplifications.
For the general OCC background see [features.md](features.md).

All endpoints live under `/api/v1`.

## 1. Why it exists

After novation OCC is the counterparty to both sides of every trade. If a
clearing member defaults, OCC must still honour that member's contracts, so
OCC is exposed to every member's open positions for as long as they are open.

- **Margin** is collateral sized to the loss a member's positions could
  plausibly cause. If the member defaults, their collateral absorbs the loss
  first ("defaulter pays"), protecting OCC and the shared clearing fund.
- **Margin must be continuous.** Every trade changes exposure, and market
  volatility changes the potential loss on the same exposure. So margin is
  recomputed on every novation and on every volatility shock.
- **A margin call** is the signal that a member's collateral requirement
  exceeds the cash they have available.
- **Settlement** is the final confirmation that a novated trade can be
  completed, and **exercise/assignment** is how long option holders realise
  value, with OCC assigning the obligation to short holders.

This is a simplified simulation. Real OCC uses a far more sophisticated
stress-testing model.

## 2. Positions

A `Position` is a member's net holding in one option symbol:
`(memberId, optionSymbol)` is unique, and `netQuantity` is positive for long
and negative for short.

- On each novated trade the buyer's position increases and the seller's
  decreases by the traded quantity.
- Opposite trades net down. Buying 10 and selling 10 leaves 0 and no exposure.
- `lastPrice` records the most recent execution price in that symbol and is
  the price basis for margin.
- Flat (zero) positions are kept as rows but hidden from queries by default.

## 3. Margin

```
margin(position) = |netQuantity| x 100 x lastPrice x volatilityFactor
marginBalance    = sum of margin(position) over the member's positions
```

- `100` is the contract multiplier, the same one used for premium.
- Long and short positions both require margin (absolute quantity).
- Values use scale 4 and `HALF_UP` rounding.

### Volatility factor

`VolatilityProvider` supplies the factor and is injectable, so tests can
replace it. The default, `ConfigurableVolatilityProvider`, resolves in this
order:

1. a shock set for that symbol,
2. a global shock,
3. the base factor `risk.volatility.base-factor` (default `0.10`).

Shocks are held in memory: they are lost on restart and are not shared across
multiple application instances.

### Margin calls

A margin call opens when `marginBalance > cashBalance` (strictly greater).
Available cash is the member's raw `cashBalance`; the credit limit is not
added.

- There is at most one OPEN call per member. While the breach lasts the call
  is updated (margin, cash, shortfall); when it clears the call is CLOSED.
- A margin call never blocks a trade. It is a demand for collateral, not a
  rejection.
- Members start with cash 0, and a buyer's cash goes negative when they pay a
  premium, so an underfunded buyer will usually have a call open after their
  first trade.

## 4. Transaction guarantees

| Operation | Changes committed together |
|---|---|
| Novation | audit, both contract legs, both ledger movements, both positions, both margin balances, margin calls |
| Volatility shock | recomputed margin and margin calls for every member holding a position |
| Settlement | leg status changes, exception records |
| Exercise | positions, cash, ledger entries, exercise and assignment records, margin, margin calls |

If any step fails, the whole operation rolls back. The one deliberate
exception is the audit of a *rejected* trade, which is recorded in a separate
transaction so it survives the rollback.

**Locking.** Member balance rows are locked with `SELECT ... FOR UPDATE` in
sorted member-id order everywhere (novation, shock, settlement, exercise), so
concurrent operations serialize per member and cannot deadlock. Position rows
are only written while the owner's balance row is locked.

If a volatility-shock transaction rolls back, the in-memory shock is restored.

## 5. Settlement

Premium cash already moves at novation, so settlement confirms that both
clearing members can still stand behind the trade.

```
NOVATED -> SETTLED
NOVATED -> FAILED -> SETTLED   (a failed settlement can be retried)
```

`SETTLED` is terminal. The status lives on the contract legs
(`NovatedContract.status`); both legs always move together.

`POST /trades/{externalTradeId}/settle` checks, for both members:

- the member is active, otherwise `INACTIVE_MEMBER`;
- the member's cash is not below minus their credit limit, otherwise
  `INSUFFICIENT_FUNDS`.

| Outcome | Result |
|---|---|
| All checks pass | Legs become SETTLED; open exceptions for the trade become RESOLVED |
| A check fails | Legs become FAILED; one OPEN exception per failing member and reason. Returned as **200** with `status: FAILED` |
| Trade never novated | 404 |
| Trade already SETTLED | 409 |

A failed settlement is a recorded business outcome, not a malformed request,
which is why it is a 200. Retrying does not create duplicate exceptions.
Concurrent attempts for one trade are serialized by locking the legs.

## 6. Exercise and assignment

`POST /options/exercise` exercises a long position, **cash-settled** at an
underlying price supplied by the caller (there is no price feed).

The symbol encodes the contract, for example `AAPL260619C00150000` is AAPL,
expiry 2026-06-19, call, strike 150.000.

```
intrinsic (call) = max(0, settlementPrice - strike)
intrinsic (put)  = max(0, strike - settlementPrice)
payout           = intrinsic x quantity x 100
```

**Assignment** is pro-rata across short holders of that symbol, in proportion
to their short size. Each gets the floor of its exact share; leftover
contracts go to the largest fractional remainders, with ties broken by member
id, so the result is deterministic. No holder is assigned more than they are
short.

**Effects:** the exerciser's long position and the assignees' short positions
shrink by the exercised and assigned quantities; each assignee pays their share
of the payout and the exerciser receives the total; margin and margin calls are
recomputed for everyone affected. Ledger entries reference the exercise id
(`EXR-...`).

**Rejections (422, nothing changes):** the option is out of the money or
exactly at the strike; the quantity exceeds the member's long position
(including a short holder trying to exercise); the member is inactive. An
unknown member is 404. If a new short holder appears while the exercise is
being prepared the call returns 409 and can be retried.

Assignees cannot block an exercise. If an assignee's cash falls short, the
margin-call mechanism flags it.

## 7. API reference

### Risk

| Method and path | Description |
|---|---|
| `GET /members/{id}/margin` | Margin balance, cash, shortfall and positions with the factor and requirement used. 404 for an unknown member |
| `GET /members/{id}/margin-calls` | Margin calls, newest first, paginated. 404 for an unknown member |
| `POST /risk/volatility` | Sets a shock. Body: `{"factor": 0.25, "symbol": "AAPL260619C00150000"}`; `symbol` is optional (omit for a global shock). `factor` must be above 0 and at most 10, with up to 6 decimals. Margin is recomputed immediately for all members holding positions |

### Settlement and exercise

| Method and path | Description |
|---|---|
| `POST /trades/{externalTradeId}/settle` | Settle a novated trade (see section 5) |
| `POST /options/exercise` | Body: `{"memberId", "optionSymbol", "quantity", "settlementPrice"}`. Returns the strike, intrinsic value, total payout and the assignments |

### Queries

All list endpoints take `page` (default 0) and `size` (default 50, maximum
200). Out-of-range values are clamped, not rejected. An invalid enum filter
returns 400.

| Method and path | Filters | Order |
|---|---|---|
| `GET /trades/log` | `memberId`, `optionSymbol`, `status` | newest first |
| `GET /positions` | `memberId`, `optionSymbol`, `includeFlat` (default false) | member, then symbol |
| `GET /settlement/exceptions` | `status` (OPEN or RESOLVED), `memberId` | newest first |
| `GET /members/{id}/margin-calls` | none | newest first |

The trade log lists contract legs, so each trade appears twice: the buyer's leg
under the buyer and the seller's leg under the seller. Filter by `memberId` to
see one member's legs.

## 8. Data model

| Table | Purpose |
|---|---|
| `positions` | Net quantity and last price per member and symbol (unique on the pair) |
| `margin_calls` | Margin calls with status OPEN or CLOSED |
| `member_ledger_balances` | `marginBalance` is now written by the risk engine |
| `novated_contracts` | `status` now also takes SETTLED and FAILED; `settled_at` added |
| `settlement_exceptions` | Failed settlements per trade and member |
| `option_exercises`, `option_assignments` | Exercise records and their assignments |

## 9. Known simplifications

- Margin is a simple volume x price x volatility formula, not a stress-test
  model.
- Volatility shocks are in memory only (not persisted or shared across
  instances).
- Exercise is cash-settled only, at a caller-supplied price. Physical delivery
  is not modelled.
- Expiry dates are not enforced, so expired contracts can be exercised.
- Settlement is triggered by an explicit call; there is no scheduled settlement
  at expiry.
- Futures delivery and securities lending are out of scope.
