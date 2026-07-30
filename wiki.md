# Farmer AutoSell Wiki

## Türkçe

### Gereksinimler ve kurulum

| Bileşen | Gereksinim |
| --- | --- |
| Farmer | v6-b125 veya daha yeni uyumlu sürüm |
| Sunucu | Paper 1.21.x / 26.x, Leaf veya Folia |
| Java | 1.21.x için Java 21, 26.x için Java 25 |
| Ekonomi/fiyat | Farmer içinde çalışan bir ekonomi ve en az bir geçerli fiyat kaynağı |

1. Sunucuyu durdurun.
2. Modül JAR dosyasını `plugins/Farmer/modules/` klasörüne yerleştirin.
3. Sunucuyu başlatın.
4. `plugins/Farmer/modules/autoseller/config.yml` içinde `status: true` yapın.
5. Farmer'ı yeniden yükleyin veya sunucuyu yeniden başlatın.

Bu modül bağımsız bir Bukkit eklentisi değildir; normal `plugins` klasörüne kurulmaz.

### Kullanım ve satış akışı

Modül açıkken Farmer ana menüsündeki modüller bölümünde **Otomatik Satış** görünür. `customPerm` iznine sahip kullanıcı ilgili Farmer için modülü açıp kapatabilir.

AutoSell, Farmer stoğu dolduğunda dışarıda kalacak miktarı işlemek için çalışır:

1. Farmer'ın kayıtlı AutoSell durumu ve gereken seviyesi yeniden doğrulanır.
2. Ürün `items` izin listesine göre denetlenir.
3. Farmer çekirdeğinden satış teklifi istenir.
4. Market sağlayıcısı destekliyorsa tam miktar için dinamik/miktar duyarlı fiyat kullanılır.
5. Dinamik fiyat yoksa sağlayıcının normal/statik fiyatı kullanılır.
6. Market fiyat vermezse Farmer `items.yml` içindeki manuel fiyata döner.
7. Geçerli fiyat yoksa satış kapalı biçimde başarısız olur; stok silinmez.
8. Satış ve sonradan kalan miktarın toplanması aynı Farmer için sıralı biçimde tamamlanır.

AutoSell market eklentisinin kendi satış komutunu veya ödeme işlemini çağırmaz. Yalnızca fiyat teklifi alır; ekonomi ödemesini Farmer yapar. Böylece çift ödeme ve çift stok tüketimi önlenir.

### Komutlar

AutoSell ayrı bir komut kaydetmez. Yönetim ve yeniden yükleme için Farmer'ın `/farmer` ve `/farmer reload` komutları kullanılır.

### İzinler

| İzin | Açıklama |
| --- | --- |
| `farmer.autoseller` | Varsayılan `customPerm`; Farmer menüsünden AutoSell durumunu değiştirmeye izin verir. |
| `farmer.admin` | Farmer yönetimi ve AutoSell güncelleme bildirimlerini alır. |

`customPerm` yapılandırmadan değiştirilebilir. `defaultStatus: false` kullanıldığında çevrimiçi bölge sahibinin otomatik satış yetkisi de bu düğümle doğrulanır. Çevrimdışı sahiplerde kaydedilmiş Farmer durumu korunarak sunucu tarafı işlem devam edebilir.

### Seviye kilidi

`required-farmer-level` bir tabanlı Farmer seviyesidir ve varsayılanı `1` değeridir.

- Değer yükseltilirse düşük seviyeli mevcut Farmer'larda AutoSell hemen etkisiz olur.
- Bekleyen satışlar ödeme yapılmadan önce gereksinimi yeniden doğrular.
- Kilitli Farmer'larda durum değiştirilemez.
- Önceki açık/kapalı tercihi silinmez.
- Farmer seviyeye ulaştığında veya gereksinim düşürüldüğünde tercih yeniden uygulanır.
- Farmer yükseltme menüsü AutoSell'in açılacağı seviyeyi gösterir.

### Yapılandırma

Dosya: `plugins/Farmer/modules/autoseller/config.yml`

#### Genel ayarlar

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `status` | `false` | Modülü ve Farmer menü girişini açar. |
| `defaultStatus` | `false` | Yeni Farmer'ların başlangıç AutoSell durumunu belirler. |
| `required-farmer-level` | `1` | AutoSell'in kullanılabildiği en düşük Farmer seviyesi. |
| `customPerm` | `farmer.autoseller` | Menüden durum değiştirme ve kısıtlı kullanım izni. |
| `items` | `[]` | Satılabilecek Farmer ürünleri. Boş liste bütün yapılandırılmış Farmer ürünlerine izin verir. |

`items` girdileri ana eklentinin `items.yml` malzeme anahtarlarıyla aynı olmalıdır:

```yaml
items:
  - WHEAT
  - CARROT
  - IRON_INGOT
```

Bu liste fiyat tanımlamaz; fiyatın yetkili kaynağı her zaman Farmer çekirdeğidir.

#### Güncelleme denetimi

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `update-checker.enable` | `true` | Kararlı AutoSell GitHub sürümlerini arka planda denetler. |
| `update-checker.check-interval-hours` | `6` | Denetim aralığı; güvenli aralık `1-168` saattir. |
| `update-checker.connect-timeout-seconds` | `5` | Bağlantı zaman aşımı; `2-30`. |
| `update-checker.request-timeout-seconds` | `8` | İstek zaman aşımı; `3-60`. |

Yeni sürüm bildirimi konsola ve çevrimiçi operatörlere veya `farmer.admin` izni olan oyunculara sürüm başına bir kez gönderilir.

#### Optimizasyon

| Ayar | Varsayılan | Açıklama |
| --- | --- | --- |
| `optimize-module.enable` | `false` | Aşağıdaki bütün üretim optimizasyonlarını açan anahtar. Kapalıyken alt değerler etkisizdir. |
| `optimize-module.processingDelayTicks` | `2` | Aynı Farmer/ürün kapasite olaylarını tek satış grubunda birleştirmek için bölge gecikmesi. |
| `optimize-module.maxPendingBatches` | `4096` | Bellekte bekleyebilecek Farmer/ürün satış grubu sınırı. |
| `optimize-module.maxBatchAmount` | `1000000000` | Tek gruba kabul edilen en yüksek ürün miktarı. |
| `optimize-module.ownerCacheSeconds` | `60` | Doğrulanmış bölge sahibi sonucunun önbellek süresi. |
| `optimize-module.guiClickCooldownMillis` | `250` | Aynı oyuncunun yinelenen modül menüsü tıklamaları arasındaki süre. |
| `optimize-module.cleanupIntervalSeconds` | `60` | Süresi dolan önbellek ve oran sınırı kayıtlarının temizleme aralığı. |
| `optimize-module.auditRejectedOperations` | `true` | Geçersiz, eşzamanlı veya sınır dışı işlemleri sunucu günlüğüne yazar. |

Optimizasyon açıkken gecikmeli satış yine kaynak konumun sahibi olan Paper/Folia bölgesine teslim edilir. Asenkron temizlik görevi Bukkit dünya, envanter, varlık veya ekonomi durumuna erişmez. Kuyruk sınırı dolarsa olay doğru bölgedeki anlık güvenli akışa döner.

### Dil dosyaları

Modül Farmer'ın seçili dilini izler ve `plugins/Farmer/modules/autoseller/lang/` altında `en.yml`, `tr.yml` ve `de.yml` dosyalarını sağlar. Modül adı, açık/kapalı/kilitli durum, seviye gereksinimi, menü açıklamaları ve güncelleme bildirimi dahil oyuncuya gösterilen metinler buradan düzenlenir.

### Otomatik dosya bakımı

Başlangıçta ve modül yeniden yüklemesinde `config.yml` ile paketli dil dosyaları denetlenir. Eksik bilinen girdiler eklenir; bozuk YAML, yanlış tür, güvenli olmayan aralık, anlamsız zorunlu değer ve bilinen bozuk kodlama metni düzeltilir. Geçerli özel değerler ve bilinmeyen genişletme anahtarları korunur.

Mevcut bir dosya değiştirilmeden önce aynı klasörde zaman damgalı `.bak-*` yedeği oluşturulur.

### Sorun giderme

- Menü girişi yoksa JAR yolunu ve `status: true` değerini kontrol edin.
- Modül kilitliyse Farmer seviyesini `required-farmer-level` ile karşılaştırın.
- Kullanıcı modülü açamıyorsa `customPerm` iznini doğrulayın.
- Ürün satılmıyorsa Farmer durumunun açık, ürünün `items` listesinde ve Farmer fiyat kaynağında geçerli fiyatı olduğunu kontrol edin.
- Market eklentisinde fiyat yoksa ana Farmer `items.yml` dosyasındaki `price` değerini doğrulayın.
- Bekleyen işlemler reddediliyorsa günlükteki denetim kaydını ve optimizasyon sınırlarını inceleyin.

### Derleme

```bash
mvn -o clean package
```

Üretilen modül JAR dosyası `target/` klasöründedir.

---

## English

### Requirements and installation

| Component | Requirement |
| --- | --- |
| Farmer | v6-b125 or a newer compatible build |
| Server | Paper 1.21.x / 26.x, Leaf, or Folia |
| Java | Java 21 for 1.21.x, Java 25 for 26.x |
| Economy/price | A working Farmer economy and at least one valid price source |

1. Stop the server.
2. Place the module JAR in `plugins/Farmer/modules/`.
3. Start the server.
4. Set `status: true` in `plugins/Farmer/modules/autoseller/config.yml`.
5. Reload Farmer or restart the server.

This module is not a standalone Bukkit plugin and does not belong in the normal `plugins` directory.

### Usage and sale flow

When enabled, **Auto Sell** appears in the modules section of the Farmer menu. A user with `customPerm` may toggle it for the current Farmer.

AutoSell processes the amount that would overflow when Farmer stock reaches capacity:

1. The saved AutoSell state and required Farmer level are revalidated.
2. The product is checked against the `items` allowlist.
3. A sale quote is requested from Farmer core.
4. If supported by the shop provider, dynamic/quantity-aware pricing is used for the exact amount.
5. If no dynamic price exists, the provider's normal/static price is used.
6. If the shop cannot quote the product, Farmer falls back to the manual `items.yml` price.
7. Without any valid price, the sale fails closed and no stock is removed.
8. The sale and subsequent collection of the remaining amount complete serially for that Farmer.

AutoSell never invokes a shop plugin's sale command or payout transaction. It requests prices only; Farmer performs the economy deposit. This prevents duplicate payouts and duplicate stock consumption.

### Commands

AutoSell registers no separate commands. Use Farmer's `/farmer` and `/farmer reload` commands for management and reloads.

### Permissions

| Permission | Description |
| --- | --- |
| `farmer.autoseller` | Default `customPerm`; allows AutoSell to be toggled through the Farmer menu. |
| `farmer.admin` | Farmer administration and AutoSell update notifications. |

`customPerm` is configurable. With `defaultStatus: false`, an online region owner's automatic-sale access is also checked against this node. A saved Farmer state can continue server-side processing while the owner is offline.

### Level gate

`required-farmer-level` is a one-based Farmer level and defaults to `1`.

- Raising it immediately disables AutoSell for existing lower-level Farmers.
- Pending sales revalidate the requirement before paying.
- Locked Farmers cannot toggle the module.
- Their previous enabled/disabled preference is retained.
- The preference applies again after reaching the level or lowering the requirement.
- Farmer's upgrade menu displays the level at which AutoSell unlocks.

### Configuration

File: `plugins/Farmer/modules/autoseller/config.yml`

#### General settings

| Setting | Default | Description |
| --- | --- | --- |
| `status` | `false` | Enables the module and Farmer menu entry. |
| `defaultStatus` | `false` | Initial AutoSell state for newly created Farmers. |
| `required-farmer-level` | `1` | Lowest Farmer level that may use AutoSell. |
| `customPerm` | `farmer.autoseller` | Permission for menu toggles and restricted use. |
| `items` | `[]` | Farmer products that may be sold. An empty list allows every configured product. |

Entries must match the main plugin's `items.yml` material keys:

```yaml
items:
  - WHEAT
  - CARROT
  - IRON_INGOT
```

This list does not define prices. Farmer core remains the authoritative price source.

#### Update checker

| Setting | Default | Description |
| --- | --- | --- |
| `update-checker.enable` | `true` | Checks stable AutoSell GitHub releases asynchronously. |
| `update-checker.check-interval-hours` | `6` | Check interval; safe range `1-168` hours. |
| `update-checker.connect-timeout-seconds` | `5` | Connection timeout; `2-30`. |
| `update-checker.request-timeout-seconds` | `8` | Request timeout; `3-60`. |

A new release is reported once per version to console and online operators or players with `farmer.admin`.

#### Optimization

| Setting | Default | Description |
| --- | --- | --- |
| `optimize-module.enable` | `false` | Master switch for all production tuning below. Children are inert while disabled. |
| `optimize-module.processingDelayTicks` | `2` | Region delay used to merge capacity events for the same Farmer/product. |
| `optimize-module.maxPendingBatches` | `4096` | Pending Farmer/product sale groups retained in memory. |
| `optimize-module.maxBatchAmount` | `1000000000` | Largest amount accepted into one group. |
| `optimize-module.ownerCacheSeconds` | `60` | Lifetime of a validated region-owner cache entry. |
| `optimize-module.guiClickCooldownMillis` | `250` | Minimum interval between repeated module-menu toggles from one player. |
| `optimize-module.cleanupIntervalSeconds` | `60` | Interval for expired cache and rate-limit cleanup. |
| `optimize-module.auditRejectedOperations` | `true` | Logs invalid, concurrent, or out-of-bound operations. |

With optimization enabled, delayed sales are handed back to the Paper/Folia region that owns the source location. Asynchronous cleanup never accesses Bukkit world, inventory, entity, or economy state. When a queue limit is reached, processing falls back to the immediate safe path on the correct region.

### Language files

The module follows Farmer's selected language and provides `en.yml`, `tr.yml`, and `de.yml` under `plugins/Farmer/modules/autoseller/lang/`. The module name, enabled/disabled/locked states, level requirement, menu descriptions, update notice, and other player-facing text are editable there.

### Automatic file maintenance

`config.yml` and bundled language files are validated on startup and module reload. Missing known entries are added; malformed YAML, wrong types, unsafe ranges, meaningless required values, and known broken-encoding text are repaired. Valid custom values and unknown extension keys are preserved.

Before modifying an existing file, a timestamped `.bak-*` copy is created beside it.

### Troubleshooting

- If no menu entry exists, check the JAR path and `status: true`.
- If the module is locked, compare the Farmer level with `required-farmer-level`.
- If a user cannot toggle it, verify `customPerm`.
- If a product is not sold, check the saved Farmer state, the `items` allowlist, and the active Farmer price source.
- If the shop has no price, verify the product's `price` in the main Farmer `items.yml`.
- If pending work is rejected, inspect audit logs and the optimization bounds.

### Building

```bash
mvn -o clean package
```

The module JAR is written under `target/`.
