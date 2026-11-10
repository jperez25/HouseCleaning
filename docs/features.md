# Clearinghouse Core Features

## Trade Novation

When a buyer and seller execute a trade on an options or futures exchange, the
Options Clearing Corporation (OCC) steps in and novates the trade. Rather than
leaving the buyer and seller exposed to each other, OCC becomes the buyer to
every seller and the seller to every buyer.

If one party defaults or goes bankrupt, OCC guarantees that the other party
still gets paid or receives their assets.

## Risk Management and Margin Collection

Because OCC guarantees every trade, it acts as the ultimate risk manager and
continuously calculates and collects collateral.

- **Margin requirements:** OCC collects daily margin from clearing members based
  on market volatility. If a stock or index moves sharply, OCC recalculates
  exposure to help ensure participants remain solvent.
- **Clearing fund:** OCC maintains a pool of pre-funded collateral shared across
  members to absorb catastrophic market shocks.

See [risk-and-settlement.md](risk-and-settlement.md) for how this project
implements positions, margin, margin calls and settlement.

## Settlement and Delivery

Clearing is not just about recording a trade; it is also about completing it.
At the end of the trade lifecycle, OCC handles final settlement, including
transferring cash, exercising or assigning options contracts, handling futures
delivery, and managing securities-lending transactions.

## Regulatory Compliance and Systemic Stability

Because a failure at OCC could affect the U.S. financial system, it operates
under direct federal oversight from the SEC, CFTC, and Federal Reserve. Every
software component, database transaction, and automated pipeline must maintain
auditability, prevent data loss, and support high-concurrency fault tolerance.
