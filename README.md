# MooshGF's Tornado (Forge 1.20.1)

Süperhücre (supercell), hortum (EF0-EF5) ve Dominator I-IV. Tamamı Minecraft'ın kendi **duman partikülleriyle** çizilir
(`LARGE_SMOKE`, `SMOKE`, `CAMPFIRE_COSY_SMOKE`, `CAMPFIRE_SIGNAL_SMOKE`). Ekstra doku/model yok.

## Komutlar
```
/mooshgfstornado                               EF3 hortum çağırır
/mooshgfstornado tornado <ef0..ef5|d1..d4> [konum]
/mooshgfstornado dominator <1-4> [konum]       Dominator I, II, III, IV
/mooshgfstornado supercell [aşama 0-3] [sınıf] [konum]
        aşama: 0 gelişiyor, 1 olgun (yağmur+şimşek), 2 hortumlu, 3 dağılıyor
/mooshgfstornado capacity                      tüm hortum kapasiteleri tablosu
/mooshgfstornado list | clear
/mooshgfstornado config blocks|entities|warnings <true/false>
/mooshgfstornado config wind|particles <değer>
```
Komut için yetki (OP) gerekmez.

## Hortum kapasiteleri
| Sınıf | Yarıçap | Yükseklik | Kapasite (havada blok) | Kırabildiği sertlik | Rüzgâr (blok/tick) |
|---|---|---|---|---|---|
| EF0 | 7 | 45 | 20 | 0.4 (yaprak, cam) | 0.45 |
| EF1 | 10 | 65 | 60 | 1.0 | 0.65 |
| EF2 | 15 | 85 | 150 | 2.5 | 0.85 |
| EF3 | 22 | 105 | 300 | 5.0 | 1.10 |
| EF4 | 32 | 130 | 600 | 12 | 1.40 |
| EF5 | 45 | 160 | 1000 | 35 (toprak/taş da kazınır) | 1.80 |
| Dominator I | 60 | 190 | 1500 | 45 | 2.10 |
| Dominator II | 85 | 220 | 2500 | 55 (obsidyen dahil) | 2.50 |
| Dominator III | 120 | 260 | 4000 | kırılamayan hariç her şey | 2.90 |
| Dominator IV | 170 | 320 | 6000 | kırılamayan hariç her şey | 3.40 |

Dominator I-IV bu modun kendi tanımıdır (kademeli, giderek devasalaşan hortumlar). Değerleri `TornadoClass.java` içinde değiştirebilirsin.

## Jar'ı derlemek

### Yol 1: GitHub ile (önerilen, bilgisayara bir şey kurmadan)
1. github.com'da yeni bir repo aç (örn. `MooshGFsTornado`).
2. Bu klasördeki **tüm dosyaları** (`.github` klasörü dahil) repoya yükle (Add file > Upload files) veya `git push` yap.
3. Repo'da **Actions** sekmesine git. "Build mod jar" çalışır (yoksa soldan seçip **Run workflow** de).
4. Bittiğinde çalışmanın sayfasında **Artifacts > MooshGFsTornado-jar** dosyasını indir, içinden `mooshgfstornado-1.0.0.jar` çıkar.

### Yol 2: Bilgisayarında
JDK 17 ve Gradle 8.1.1 kur, klasörde `gradle build` çalıştır. Jar `build/libs/` içinde çıkar.

## Kurulum
Minecraft 1.20.1 + Forge 47.x. Jar'ı `mods` klasörüne at.

## Notlar
- Büyük sınıflar (EF5, Dominator) ve çok partikül ağırdır. FPS düşerse `/mooshgfstornado config particles 0.5` kullan.
- Hortumlar dünya kaydedilirken saklanmaz (`noSave`), dünyayı yeniden açınca kaybolur.
- Bloklar "düşen blok" olarak havaya kaldırılır; kapasite dolunca yeni blok almaz. Sandık gibi blok varlıkları (envanter) bilerek alınmaz.
