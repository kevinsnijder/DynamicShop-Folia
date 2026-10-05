# Release notes

## 3.0.4

This update fixes the remaining problems with custom items. In 3.0.3, an item could be added to the right category but still behave like a purchase-only Server Shop entry. It could also be missing from that category in the web editor. Custom items can now be traded and managed in their assigned categories.

- **Players can sell custom items.** This covers physical items from Oraxen, Nexo, ItemsAdder, and ValhallaMMO, as well as saved custom item variants. Right-click selling in the shop and player inventory works, along with `/shop sellhand`, `/shop sellall`, and `/shop sellall hand`.
- **Each custom item has its own stock and settings.** Its price, stock, storage limits, and buying/selling switches are separate from the ordinary Minecraft item underneath it. Selling a custom watering can, for example, updates that can's stock rather than the stock of its base material.
- **Sales match the actual custom item.** Ordinary items and other custom variants that share the same base material are left alone. `/shop sellhand` sells only the stack in your hand; `/shop sellall hand` sells matching stacks throughout your inventory.
- **Custom items appear in the web category editor.** You can change their name, category, price, stock, shortage settings, and availability. The bulk buying and selling controls also work with custom entries.
- **Removing a custom entry now removes it from the shop configuration.** The web editor no longer just hides it. You can still hide an entry temporarily with the Item disabled switch.
- **Open menus use the current item settings when you trade.** Price changes, permissions, and disabled entries are checked again before the transaction. If a custom item provider is unavailable or an entry has been deleted, the trade is rejected instead of substituting an ordinary item. Permissions, commands, and other rewards remain purchase-only.
- **Website update checks follow website changes.** A plugin version change alone no longer asks you to replace unchanged web files. Updated bundles and missing files are detected, while manual updates continue to back up your existing files.

To update, replace the plugin JAR and restart the server. Run `/shopadmin webupdate`, then refresh your browser to load the new editor.

Existing custom categories and delivery IDs are kept. Older entries without a category stay in Server Shop; you can move them using the category field in the web editor. Review the starting stock and storage limits for existing custom entries, since their stock is now tracked independently.

Validation: 108 Java tests and 7 website tests pass, including custom-item sales, stock limits, mixed bulk edits, and removal. Browser checks cover category visibility, saving custom entries, and selling-switch persistence. Testing on the reporting server is still needed.

## 3.0.3

- Website update checks now use content revisions rather than the plugin version. Plugin-only updates and line-ending differences no longer trigger website updates; changed bundles are detected even within the same plugin version. Existing files, customizations, and backups retain the manual-update behavior.
- Added `/shop sellall hand` to sell all sellable stacks matching the held item across the inventory, using the existing `dynamicshop.use.sellall` permission. It preserves item-template checks, stock limits, pricing, cooldowns, and payment handling; plain `/shop sellall` still sells all sellable types.
- Fixed `/shopadmin add item <price> [category]` ignoring the category for Oraxen, Nexo, ItemsAdder, and ValhallaMMO items. These entries now use the requested category (or the normal material category when omitted), persist it across reloads, and retain their custom delivery method. Existing entries can be moved by setting `special_items.<id>.category` and running `/shopadmin reload`.
- Resolved Java formatting and compatibility findings and simplified command, transaction, GUI, pricing-data, and web-admin code without changing their intended behavior.
- Removed duplicate configuration and message keys while retaining their effective values, and consolidated repeated stock, inflation, and trading-instruction lore.
- Corrected website request options and admin access handling. Web bundles now use consistent line endings so Windows and Linux produce identical cache hashes.
- Added regression tests and CI checks for Java quality, valid bundled YAML, website requests, and generated web assets. Generated bundles are excluded only from duplicate-code analysis; the source remains checked.

## 3.0.2

- Added DSX Exchange as the sixth selectable website design, with a quote board, ask/bid prices, spread, stock, recent trade ticker, and recorded transaction activity. It shares the catalog filters, watchlist, item history, and administration controls.
- Added eight complete color palettes and expanded accent choices to twelve. Each design retains its own appearance settings.
- Added one combined bStats chart for Oraxen, Nexo, ItemsAdder, and No custom adder. Servers with multiple supported adders contribute to each enabled integration.
- Added bStats charts for online player ranges, custom GUI configuration, dynamic pricing, time-inflation adoption and activity, and completed transaction counts over a bounded 30-minute window. The inflation activity chart measures trades while inflation is enabled, not proof that it increased a price.
- Kept transaction metrics bounded in memory, cached their enabled setting, and removed item construction from GUI detection. Reports use bStats' existing collection schedule and background submission.
- Includes the Oraxen integration, Floodgate-based Bedrock GUI fallback, message formatting fixes, and saved-appearance loading fix from the preceding 3.0.1 work.

Install the new JAR and restart. Run `/shopadmin webupdate` to back up and replace bundled website files, then select DSX Exchange in Administration → Appearance if desired. Custom edits to bundled web files are backed up before replacement; server settings and shop data are retained.

The plugin owner must register the custom chart IDs and types described in [BSTATS.md](BSTATS.md). The code does not automatically create charts on the bStats dashboard.

Validation: 72 automated tests pass; browser checks cover DSX selection and persistence, quote filtering, empty results, watchlist actions, item history, and the shared palette editor. The [GitHub Pages demo](https://selfservice0.github.io/DynamicShop/?design=dsx#catalog) contains demonstration data.
