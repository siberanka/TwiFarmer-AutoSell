# Farmer AutoSeller Module

Paper-only AutoSeller module for Farmer v6. It sells a farmer's stocked items when capacity is reached and deposits the proceeds through Farmer's configured economy integration.

Maintained by Geik and siberanka.

## Compatibility

- Minecraft/Paper `1.21.x` through `26.x`
- Folia and Leaf, using Paper's region and async schedulers
- Java 21 bytecode; Paper 26.x server runtime requires Java 25
- Farmer v6 `v6-b125` or newer compatible builds

Plain Bukkit and Spigot servers are intentionally unsupported. The module fails closed and registers no listeners if the Paper runtime API is unavailable.

## Installation

1. Install Farmer v6-b125 or newer on Paper, Folia, or Leaf.
2. Place `Farmer-AutoSeller-2.0.5.jar` in `plugins/Farmer/modules/`.
3. Restart the server.
4. Edit `plugins/Farmer/modules/autoseller/config.yml` and set `status: true`.

Use a full restart for module upgrades. Server/plugin hot reloads are not recommended.

## Configuration

```yaml
status: false
defaultStatus: false
required-farmer-level: 1
customPerm: farmer.autoseller
items: []

update-checker:
  enable: true
  check-interval-hours: 6
  connect-timeout-seconds: 5
  request-timeout-seconds: 8

optimize-module:
  enable: false
  processingDelayTicks: 2
  maxPendingBatches: 4096
  maxBatchAmount: 1000000000
  ownerCacheSeconds: 60
  guiClickCooldownMillis: 250
  cleanupIntervalSeconds: 60
  auditRejectedOperations: true
```

- `status` enables the AutoSeller module.
- `defaultStatus` enables AutoSeller by default for every farmer.
- `required-farmer-level` is the one-based Farmer level that unlocks Auto Sell and defaults to `1`.
- `customPerm` controls access when default status is disabled.
- `items` is a Farmer item-name allowlist; an empty list allows every configured Farmer item.
- `update-checker.enable` defaults to `true`. Checks use asynchronous HTTPS against only the fixed `siberanka/TwiFarmer-AutoSell` GitHub repository.
- Update messages contain the AutoSeller module name, installed/latest versions, and a validated release download link. They are sent once per release to the console and to operators or players with `farmer.admin`.
- The interval and connection/request timeouts are bounded and repaired automatically. Reload/disable cancels or invalidates pending checks.
- `optimize-module.enable` is the master switch for all optimization sub-settings. Every setting below it is inert while it is `false`.
- `processingDelayTicks` batches capacity events on the owning Paper region, reducing economy calls under high load.
- `maxPendingBatches` and `maxBatchAmount` bound memory and malformed-input exposure. When a bound is reached, the module falls back to the immediate safe path.
- `ownerCacheSeconds` caches validated region-owner UUIDs.
- `guiClickCooldownMillis` rate-limits repeated GUI toggles.
- `cleanupIntervalSeconds` controls async cleanup of cache-only data. The async task never touches Bukkit world, entity, inventory, or economy state.
- `auditRejectedOperations` logs rejected concurrent or malformed actions.

The optimization module defaults to disabled to preserve legacy behavior. Delayed sales are always handed back to the region that owns the source location; no Bukkit world state is accessed from the async cleanup task.

## Automatic YAML repair

On startup and module reload, AutoSeller checks `config.yml` and all bundled `en.yml`, `tr.yml`, and `de.yml` language files. Missing entries are merged automatically. Invalid types, unsafe ranges, malformed YAML, meaningless required values, and known broken-encoding text are replaced with safe defaults only after a timestamped `.bak-*` copy is created beside the original file.

Valid custom values and unknown extension keys are preserved.

Raising `required-farmer-level` takes effect immediately, including for queued
sales. Locked Farmers retain their saved module preference without being able to
toggle or run Auto Sell; the preference becomes effective again after the Farmer
reaches the configured level or the requirement is lowered.

## Building

```bash
mvn -Ppaper-1.21 clean verify
mvn -Ppaper-26 clean verify
```

The release JAR is written to `target/Farmer-AutoSeller-2.0.5.jar`.

## Security and operational notes

- Farmer core is the sole pricing authority. AutoSeller accepts valid market, dynamic, and per-item `items.yml` fallback pricing without pre-filtering on the raw manual price.
- Sale operations are serialized per farmer to prevent concurrent double-sell races across Folia regions.
- Delayed batches cancel the matching physical spawn only after the bounded server-side batch accepts the amount.
- Sale state is revalidated before execution and the resulting stock amount is verified afterward.
- Disable/reload stops new work; already accepted region-owned batches are allowed to drain so their cancelled item spawns are not lost.

## License

MIT. See [LICENSE](LICENSE).
