# Outlaw Economy

Outlaw Economy is a Paper/Spigot plugin that powers a centralized server economy with NPC shops, a shared general marketplace, and a secure player-to-player trading system.

## Requirements
- Java 17 or newer
- PaperMC/Spigot 1.20.4
- (Optional) Vault for compatibility with other economy-aware plugins

## Building
```bash
mvn -q -DskipTests package
```
The plugin JAR will be generated at `target/outlaw-economy-1.0.0-SNAPSHOT.jar`.

## Installation
1. Place the compiled JAR inside your server's `plugins/` folder.
2. Start the server to generate `config.yml`, `balances.yml`, and `shops.yml` under `plugins/OutlawEconomy/`.
3. Stop the server and adjust the configuration if needed, then restart.

## Configuration Overview
`plugins/OutlawEconomy/config.yml`
```yaml
economy:
  starting-balance: 100.0   # Initial money for first-time players
  currency-name: "$"
trade:
  request-timeout: 30       # Seconds before an unanswered trade request expires
```
Additional data files:
- `balances.yml` stores player balances.
- `shops.yml` stores NPC shop placements and templates.

## Commands
| Command | Description | Permission |
| --- | --- | --- |
| `/balance` | Show your own balance. | *(everyone)* |
| `/balance <player>` | View another player's balance. | `outlawecoadmin` |
| `/balance all` | List every stored balance. | `outlawecoadmin` |
| `/pay <player> <amount>` | Send money to another player. | *(everyone)* |
| `/givemoney <player> <amount>` | Add money to a player's balance. | `outlawecoadmin` |
| `/removemoney <player> <amount>` | Remove money from a player's balance. | `outlawecoadmin` |
| `/trade <player>` | Request a secure trade with another player. | *(everyone)* |
| `/trade accept|deny|cancel` | Respond to the latest trade request or cancel an active trade. | *(everyone)* |
| `/shop open <template>` | Open an NPC shop template menu. | `outlaweco.command.shopopen` |
| `/shop open general` | Open the shared general marketplace. | `outlaweco.command.shopopen` |
| `/shop create <template|general>` | Create an NPC shop or place the general market NPC. | `outlawecoadmin` |
| `/shop remove` | Remove the targeted NPC shop. | `outlawecoadmin` |
| `/shop list [templates]` | List placed shops or available templates. | `outlawecoadmin` |
| `/shop add itemshop <template> <item> <quantity> <buyPrice> [sellPrice]` | Add an item entry to a simple template. | `outlawecoadmin` |
| `/shop removeitem <template> <item>` | Remove an item entry from a simple template. | `outlawecoadmin` |
| `/shop reloadtemplates` | Reload shop templates from disk. | `outlawecoadmin` |
| `/shop setting price` | Open the price configuration menu for shop items. | `outlawecoadmin` |
| `/shop setting overallprice <template> [buy|sell] <multiplier|reset>` | Adjust buy/sell multipliers across a template. | `outlawecoadmin` |

## Permissions
| Permission | Purpose | Default |
| --- | --- | --- |
| `outlaweco.use` | Allow players to open shop menus triggered by NPCs or commands. | `false` |
| `outlaweco.command.shopopen` | Allow direct usage of `/shop open` from chat. | `false` |
| `outlawecoadmin` | Grant full administrative access to economy and shop management features. | `op` |

## API Usage
Outlaw Economy exposes a static API and a Vault bridge:
- `EconomyAPI` for direct calls within other plugins.
- `EconomyService` via Bukkit's `ServicesManager`.
- Vault `Economy` service automatically registered when Vault is present.

Ensure your dependent plugins declare `depend` or `softdepend` on `OutlawEconomy` (and `Vault` if needed).

## Support
For issues or feature requests, open an issue on the repository or contact the OutlawMC team.
