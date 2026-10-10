# BlockBank

A Paper server economy and GUI shop plugin.

## Features

- `/shop` opens a category GUI and lets players buy configured items.
- `/sell` opens a separate selling GUI. Put items in the top 45 slots, then click the green glass pane. The player's hotbar and normal inventory are never scanned for selling.
- `/money` displays your balance.
- `/shopadmin` opens an operator shop editor. Left-click an item to toggle whether it appears in the shop; right-click to toggle whether it can be sold.
- Admin commands create categories, add materials, and adjust prices.
- Balances are saved in `plugins/BlockBank/balances.yml`; shop prices/categories are in `plugins/BlockBank/config.yml`.

## Requirements

- Paper 26.2 server
- Java 25
- Maven 3.9+ if building locally

## Build locally

```bash
mvn clean package
```

The JAR will be `target/BlockBank.jar`.

## GitHub Actions build

Push these files to the repository's `main` or `master` branch. Open the **Actions** tab, select **Build BlockBank**, and download the `BlockBank-plugin` artifact from a successful run. Artifacts are retained for 30 days.

## Admin commands

Only operators or players with `blockbank.admin` may use these.

```text
/shopadmin
/shopadmin category <id> <name words...> <icon-material>
/shopadmin add <category> <MATERIAL> <buy-price> <sell-price>
/shopadmin price <MATERIAL> <buy-price> <sell-price>
/shopadmin sellable <MATERIAL> <true|false>
/shopadmin list <MATERIAL> <true|false>
```

Example:

```text
/shopadmin category decorations Decorations FLOWER_POT
/shopadmin add decorations LANTERN 20 5
/shopadmin price DIRT 2 0.5
/shopadmin sellable DIAMOND_BLOCK false
/shopadmin list DIRT true
```

Material names must be valid Bukkit/Paper `Material` enum names. Prices must be non-negative. `/shopadmin add` requires an existing category.

## Important notes

- This is an initial implementation; test it on a backup/test server before using it on a live economy.
- Only materials configured under a category are sellable. Items not configured in the shop are returned to the player when the sell GUI closes.
- Prices in the shop are per item. Right-click buys 64, provided the player has enough money and inventory space.
