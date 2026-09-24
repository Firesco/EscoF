# Esco'S Fork 0.7.0 — 26.2 test ve teslim raporu

> Tarihsel rapor: Bu belge EscoF adlandırmasından önceki 0.7.0 sürümüne aittir.
> Eski marka, commit ve JAR hash'leri ölçülen sürümü tanımlar; yeni EscoF
> derlemesinin doğrulandığı anlamına gelmez. Güncel derleme ve çalıştırma
> adımları için kök README'yi kullanın. Yayımlanan kayıtlardaki yerel dosya
> yolları anonimleştirilmiştir; sayısal sonuçlar değiştirilmemiştir.

Tarih: 22 Eylül 2026. Bu paket Leaf 26.2 build 118 üzerinde gerçek kaynak değişiklikleriyle derlenmiş bir Paper/Spigot sunucu forkudur. Yeni iş havuzu, aktivasyon, sensör sıralama, doğma yoğunluğu, POI ve yol oluşturma değişikliklerini içerir.

Yoğun aktivasyon senaryosunda Esco 4 worker ile Leaf'e göre **%61,62 daha düşük ortalama tick süresi** ölçüldü. Sonuç yük türüne bağlıdır; aşağıdaki tabloda gerilemeler de vardır. **Bütün yüklerde Leaf'ten hızlı olduğu, Folia'nın her kullanımını karşıladığı veya bütün eklentileri desteklediği kanıtlanmış değildir.**

## Teslim edilen sürüm

| Alan | Değer |
|---|---|
| Minecraft / Esco | 26.2 / 0.7.0 |
| Java | 25; ölçümde Temurin 25.0.4.1+1-LTS |
| Leaf temel sürüm | 26.2 build 118 |
| Leaf commit | `4b38592a41f9df8fd9b656c21ac1df3f4ada9473` |
| Paper commit | `e5fe71723e2ffde7cc9fafc085ac3bb73e63175e` |
| Son runtime kaynak commit | `f9ff0427440fd0d8705079f740648557709036d5` |
| Temiz derleme import commit | `ccc7fdd30d376d317b482050d3e6020e7d0ebb84` |
| Esco JAR boyutu | 85.261.254 bayt |
| Esco JAR SHA-256 | `2bca7fbad7670879b55986de52e75f7a763e82e4afc2236f843460f924f4bef8` |
| Karşılaştırılan Leaf JAR SHA-256 | `bfc308dc53560fe2d0158564b5382c81bc2c22cc1d714a40ee6304d7e40c385e` |
| Her iki sunucudaki aynı benchmark JAR SHA-256 | `8d260d5efd03a20ec31b4524689d20653dbe20da4979d46563bcaf19f1aad417` |

Leaf'in bu sabit sürümü upstream tarafından erken geliştirme/test sürümü olarak yayımlanmıştır. Leaf bu sürümün üretimde kullanılmamasını istiyor; Esco aynı temel üzerine kuruludur. [Leaf 26.2 sürüm kaydı](https://github.com/Winds-Studio/Leaf/releases/tag/ver-26.2).

Bu paket yalnızca 26.2 içindir; 1.21.x derlemesi içermez. Bu çalışma alanında erişilebilir eski kaynak 0.4.0 idi. Önceki 0.6 ikilisi mevcut olmadığı için 0.6 ile doğrudan ölçülmüş bir üstünlük iddiası yoktur.

## Ana performans sonucu

Birim **ms/tick**, düşük değer iyidir. Her hücre iki bağımsız JVM koşusunun ortalamasıdır. Yüzde, `100 × (1 − Esco / Leaf)` formülüdür: pozitif iyileşme, negatif gerilemedir. Hiçbir örnek veya koşu çıkarılmadı.

| Yük | Leaf | Esco 4 | Esco all=8 | 4 ile azalma | all ile azalma |
|---|---:|---:|---:|---:|---:|
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | 92,34 | 35,44 | 36,66 | 61,62% | 60,29% |
| Sensör: 8.192 aday | 19,95 | 20,34 | 56,92 | -1,96% | -185,34% |
| Sensör: 32.768 aday | 90,83 | 91,23 | 91,16 | -0,45% | -0,36% |
| Doğma yoğunluğu: 8.192 nokta | 2,04 | 2,37 | 1,86 | -16,10% | 9,19% |
| Doğma yoğunluğu: 65.536 nokta | 10,95 | 8,71 | 9,03 | 20,50% | 17,55% |
| Doğma yoğunluğu: 262.144 nokta | 36,86 | 26,60 | 26,69 | 27,82% | 27,59% |
| Normal AI: 256 köylü | 4,28 | 33,01 | 3,20 | -670,94% | 25,34% |

8.192 adaylı sensörün `all` koşularında uzun duraklamalar vardır. Bu senaryoda yeni sıralama eşiği 65.536 olduğu için **Esco worker'ı açılmadı**. Dolayısıyla bu farkı 8 worker'ın maliyeti olarak açıklamak doğru olmaz. Bu kayıtlar tabloya olduğu gibi dahildir; aşağıdaki inceleme bölümüne bakın.

8.192 noktalı yoğunluk işi de paralellik eşiğinin altındadır ve upstream yolunu kullanır. Bu satırdaki fark çok çekirdek kazancı değildir. Normal AI kontrolü stokastiktir; dünya ve ayarlar aynı olsa da Paper entity RNG tohumlamasını reddeder. Bu kontrolün sonucu kesin deterministik hızlanma olarak sunulmaz.

### Koşular arasındaki değişkenlik

Aralık, iki koşunun ortalama tick sürelerinin en küçüğü–en büyüğüdür. P95 sütunu koşuların P95 değerlerinin ortalamasıdır; bütün örneklerin birleşik P95'i değildir. Çağrı ölçümü testin kontrol/checksum maliyetini de içerir. Normal AI satırındaki çağrı sütunu yalnızca boş test görevini ölçer ve AI süresi olarak kullanılamaz.

| Yük | Sunucu | Koşu ortalamaları aralığı (ms) | Ortalama P95 tick (ms) | Ortalama çağrı (ms) |
|---|---|---:|---:|---:|
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | Leaf | 84,97–99,70 | 103,63 | 16,49 |
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | Esco 4 | 32,69–38,19 | 49,13 | 2,27 |
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | Esco all=8 | 36,06–37,27 | 50,00 | 2,15 |
| Sensör: 8.192 aday | Leaf | 19,94–19,96 | 25,23 | 1,87 |
| Sensör: 8.192 aday | Esco 4 | 19,45–21,23 | 30,59 | 1,76 |
| Sensör: 8.192 aday | Esco all=8 | 35,45–78,40 | 47,03 | 9,26 |
| Sensör: 32.768 aday | Leaf | 90,01–91,64 | 106,54 | 8,11 |
| Sensör: 32.768 aday | Esco 4 | 89,95–92,51 | 119,57 | 7,40 |
| Sensör: 32.768 aday | Esco all=8 | 90,03–92,28 | 117,82 | 7,80 |
| Doğma yoğunluğu: 8.192 nokta | Leaf | 2,02–2,07 | 3,00 | 0,04 |
| Doğma yoğunluğu: 8.192 nokta | Esco 4 | 1,89–2,86 | 2,91 | 0,04 |
| Doğma yoğunluğu: 8.192 nokta | Esco all=8 | 1,84–1,87 | 2,34 | 0,04 |
| Doğma yoğunluğu: 65.536 nokta | Leaf | 10,53–11,37 | 15,08 | 0,30 |
| Doğma yoğunluğu: 65.536 nokta | Esco 4 | 7,95–9,46 | 10,75 | 0,24 |
| Doğma yoğunluğu: 65.536 nokta | Esco all=8 | 8,98–9,08 | 11,83 | 0,25 |
| Doğma yoğunluğu: 262.144 nokta | Leaf | 36,79–36,93 | 43,13 | 1,12 |
| Doğma yoğunluğu: 262.144 nokta | Esco 4 | 25,38–27,83 | 35,62 | 0,78 |
| Doğma yoğunluğu: 262.144 nokta | Esco all=8 | 22,34–31,05 | 35,73 | 0,79 |
| Normal AI: 256 köylü | Leaf | 3,22–5,34 | 12,35 | 0,00 |
| Normal AI: 256 köylü | Esco 4 | 6,36–59,65 | 180,34 | 0,05 |
| Normal AI: 256 köylü | Esco all=8 | 2,27–4,13 | 6,78 | 0,00 |

İki tekrar ve paylaşılan test ortamı genel performans üstünlüğünü kanıtlamaz. Sonuçları başka donanıma, yüzlerce gerçek oyuncuya veya eklenti paketine doğrudan taşımayın.

## Gerçek worker kullanımı ve ölçeklenme

| Yük | Ayar | Ortalama tick (ms) | Aynı anda aktif Esco worker tepe sayısı | Ana thread CPU (ms / 200 örnek) | Esco worker CPU toplamı (ms / 200 örnek) |
|---|---|---:|---:|---:|---:|
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | esco0 | 42,46 | 0 | 8462,27 | 0,00 |
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | esco4 | 35,44 | 4 | 6521,38 | 1107,36 |
| Aktivasyon: 16.384 entity / 32 oyuncu konumu | esco_all | 36,66 | 8 | 6614,09 | 1123,89 |
| Doğma yoğunluğu: 262.144 nokta | esco0 | 45,83 | 0 | 9183,53 | 0,00 |
| Doğma yoğunluğu: 262.144 nokta | esco4 | 26,60 | 4 | 3845,92 | 7908,08 |
| Doğma yoğunluğu: 262.144 nokta | esco_all | 26,69 | 8 | 2861,18 | 8147,84 |

`workers=0`, Esco algoritmalarını seri çalıştırır. Böylece algoritma/veri düzeni kazancı ile ek worker katkısı ayrılabilir. Ana thread de uygun işlerin bir dilimini hesaplar; worker tepe sayısına dahil değildir. Worker CPU toplamı bütün worker'ların toplamıdır, duvar saati süresi değildir. Tepe sayaçları JVM ömrünün tamamını, CPU farkları ölçüm penceresini kapsar.

65.536 noktalı iş, 16.384'lük görev eşiği nedeniyle üç worker ve çağıran thread'e bölünür. `all` kullanılmasına rağmen her işte havuzun tamamını çalıştırmak hedeflenmez. Daha büyük yoğunluk işi ve aktivasyon işi 8 worker'a ulaşır. Ayrı doğruluk testi 64 worker'ın aynı anda katılımını doğruladı; bu **64 fiziksel çekirdekte performans ölçümü değildir**.

4 veya 32 worker sınırı kaldırılmıştır. `all`, JVM'in gördüğü işlemci sayısını sınır yapar; `auto` iki işlemci pay bırakır. Java havuzunun teknik üst sınırı 32767'dir. Daha fazla thread ayırmak, CPU süresinin tamamını bu havuza tahsis etmez. Leaf, GC, ağ ve chunk işleri ayrıca thread kullanır.

## Test ortamı ve eşitlik denetimi

- Host CPU bildirimi: `AMD EPYC 9V74 80-Core Processor`; işletim sistemi işlemci sayısı `9`. Her JVM `-XX:ActiveProcessorCount=8` ile çalıştı. `all` bu nedenle 8'dir. CPU affinity sabitlenmedi; ortam paylaşımlıdır.
- Her iki JAR: Java 25, G1, sabit 2 GiB heap, aynı dünya tohumu 26022026, düz dünya, 169 force-loaded chunk, aynı görüş/simülasyon mesafesi. Spark/JFR ana karşılaştırmada kapalıdır.
- Leaf profili her varyantta aynı: async mob spawning açık; async pathfinding 4; async tracker 4; parallel world ticking kapalı.
- 200 ısınma + 200 ölçüm tick'i; her kombinasyon iki ayrı JVM'de, ikinci tur ters sırayla. Toplam **46 JVM / 9.200 ölçüm tick'i**.
- Aktivasyon çağrısı tick başına 4; sensör çağrısı 4; yoğunluk çağrısı 32. Bu zorlanmış yükler normal oyuncu davranışının birebir kopyası değildir.
- Oyuncu konumları geçici server-side fixture'dır: bağlı ağ istemcisi, paket trafiği, envanter etkileşimi veya gerçek oyuncu tick'i yoktur. Maksimum oyuncu kapasitesi ölçülmedi.
- Altı deterministik senaryoda **her ölçüm tick'inin checksum dizisi** bütün varyantlarda birebir eşittir. Normal AI kontrolü bu eşitlik iddiasına dahil değildir.
- 46 koşunun JAR ve test eklentisi hash'leri, örnek sayıları, worker limitleri ve ortak ayarları bağımsız denetimden geçti. Ortak yapılandırma farkları marka metinleri, rastgele Xaero harita kimliği ve kapalı yönetim servisinin rastgele parolasıdır. Sonuncusu teslim kanıtlarında silinmiştir.
- Test sunucuları localhost/offline modundadır. Günlüklerde Minecraft public-key servisine DNS erişim hataları vardır; sunucu açılışını ve doğrulama adımlarını engellemedi.

## Uyumluluk ve doğruluk

Aynı testler sabit Leaf JAR'ı ve teslim edilen son Esco JAR'ında geçti:

| Kontrol | Sonuç |
|---|---|
| Bukkit eklenti yaşam döngüsü / API kontrolleri | 19 geçti |
| Paper API, scheduler, event ve chunk callback kontrolleri | 1.598 geçti |
| LuckPerms 5.5.85 | Grup oluşturma ve kalıcı kaydetme geçti |
| Vault 1.7.3-b131 | LuckPerms permission köprüsü geçti |
| WorldEdit 7.4.5+7590-b8dc4c1 | Gerçek blok düzenleme geçti |
| Esco canlı aktivasyon referans kontrolü | 560 doğrulama, 0 uyuşmazlık |
| POI karşılaştırması | Sabit upstream referansıyla 60 sorgu ve boş/dolu/occupancy/predicate/hata/removal kontrolleri geçti |

Bağımsız hesaplama testleri `0,1,2,4,8,16,32,64,-1,all` worker ayarlarında geçti: ayar başına 2.394.245 sıralama değeri, 709.737 yoğunluk katkısı, 171.342 aktivasyon kararı. IEEE NaN/sonsuz/işaretli sıfır, eşit değerlerin sırası, bit düzeyinde yoğunluk toplamı, mutable/custom BlockPos davranışı, iç içe çağrı, nesne referanslarının temizlenmesi ve hata halinde bütün görevlerin beklenmesi kontrol edildi. Geçersiz worker değerleri reddedildi; farklı işlemci sayılarında `auto` hesabı kontrol edildi.

Bu sayılar eklenti evreninin tamamını temsil etmez. Eklentinin kendi 26.2 desteği, NMS kullanımı ve Leaf ayarları ayrıca önemlidir. Eklentilerin senkron olayları ve scheduler görevleri ana thread'de kalır. Folia bağımsız bölgelerin tick döngülerini paralelleştiren farklı bir mimaridir; burada Folia ile doğrudan performans testi yapılmadı. [Folia mimarisi ve eklenti uyumluluğu](https://github.com/PaperMC/Folia).

Normal AI kontrolünün `esco4-2` koşusu ayrıca belirgin gecikmeler içerir: ortalama 59,65 ms, medyan 2,12 ms, P95 347,11 ms ve en yüksek tick 1.083,66 ms. Ölçüm penceresinde GC ve Esco worker çalışması yoktur. Bu koşunun kesin nedeni de belirlenmedi; düşük medyan, yüksek gecikmelerin yok sayılması için gerekçe değildir. Bu nedenle bu sürüme bütün yüklerde daha düşük gecikme garantisi verilmez.

## Neler geliştirildi?

- Caller'da alınan entity/player kopyaları üzerinde paralel aktivasyon hesabı; sonuçlar bütün worker'lar bitince caller'da uygulanır. Leaf öncelik, AFK ve spectator kuralları korunur. DAB, dragon parçaları ve dünya oyuncu listesi dışında Player/NPC görüldüğünde upstream yoluna dönülür.
- Bir dış havuz girişinden dağıtım; caller'ın hesaplamaya katılması; küçük işlerde seri yol; hatada tüm başlatılmış işlerin katılım bariyeri.
- Mesafe sıralamasında tek kararlı 11-bit radix; büyük işlerde pipeline ve sıralama rank'ına göre bölünmüş paralel birleştirme. Eşit mesafede kararlılık Paper davranışını hedefler; Leaf'in eski kararsız eşit-mesafe sırası değişebilir.
- Doğma yoğunluğunda ayrı primitive koordinat/charge dizileri, tekrar kullanılabilen bellek ve büyük görev taneleri. Son toplama sırası değişmediği için sınırdaki doğma kararlarını değiştirecek toplama yeniden gruplaması yapılmaz.
- Yalnızca ilgili yüklü chunk'ların gerçekten boş olduğu doğrulanırsa POI aramasını bitirme; zorunlu chunk yüklemesi veya sonuç önbelleği yok.
- Sensör görünürlük cache'ini ilk kullanımda ayırma; yayınlanmış aday listelerinin sahipliğini koruma; yol oluşturmayı doğrusal hale getirme.

İlk A adayının sensör kazancı doğrulanamadı; bu sonuçlar `pilot-a` kanıtları içinde tutuldu. Nihai sürümde sıralama eşiği 65.536'ya, yoğunluk görev büyüklüğü 16.384'e çıktı; seri sıralama gereksiz birleştirme aşamalarından arındırıldı. Mikro ölçümler tek başına sunucu kazancı sayılmadı. `ablation8`, aynı A JAR'ındaki eski dağıtım ayarlarıdır; erişilemeyen 0.6'nın ikilisi değildir. Eski ayarı yanlışlıkla devralan bir kernel tanı ölçümü de açık açıklamayla saklandı, varsayılan B sonucu olarak kullanılmadı.

## Sensör duraklaması incelemesi

Ana serinin `sensor_dense/esco_all-2` koşusunda en uzun tick 11.375,66 ms'dir; `esco_all-1` de 824,54 ms tepe içerir. İki koşuda Esco compute thread'i, paralel batch ve ölçüm penceresinde GC kaydı yoktur. Watchdog'un tek thread dump'ı `BenchPlugin.java:175` checksum döngüsünü gösterir. Bu, çalışan world/worker hesabında bir deadlock kanıtı değildir; duraklamanın kesin nedenini de tek başına belirlemez. Olumsuz ölçüm korunur ve üretim gecikmesi hakkında kesin bir güvence verilmez.

Aynı son JAR ve değişmeyen test eklentisiyle Leaf / Esco 4 / Esco all=8 üzerinde ayrı JFR tanısı yapıldı: 200 ısınma, 400 ölçüm tick'i, aynı sensör yükü. Üç checksum dizisi birebir eşleşti. Bu profil kaydı açık koşular **ana tabloyla birleştirilmedi**.

| JFR kontrolü | Ortalama tick (ms) | P95 (ms) | Maksimum tick (ms) | Ölçüm GC zamanı (ms) |
|---|---:|---:|---:|---:|
| leaf | 18.94 | 27.57 | 62.24 | 42 |
| esco4 | 21.92 | 31.71 | 258.52 | 58 |
| esco_all | 20.46 | 28.40 | 150.40 | 54 |

11 saniyelik duraklama bu kontrolde tekrarlanmadı. Buna rağmen iki Esco kontrolünün ortalaması Leaf'ten yüksektir; sensör üstünlüğü iddia edilmez. Örneklenen ana thread stack'lerinde entity sorgusu ve normal entity tick maliyetleri öne çıktı. Bu ayrı kayıt, önceki duraklamanın kesin nedenini belirlemiyor. JFR stack/GC/CPU özetleri ve tüm tick örnekleri `sensor-diagnostic-070/` kanıtlarına eklendi; büyük JFR iz dosyaları pakete dahil edilmedi.


## Derleme, kanıtlar ve çalıştırma

Son JAR, kaynak dışa aktarımından hazırlanan ayrı klasörde derlendi. Hazırlanmış 8.811 Java dosyasının tamamı ana kaynakla byte düzeyinde eşleşti. Build helper `applyPaperSingleFilePatches`, `applyAllPatches`, ardından `createPaperclipJar` sırasını kullanır; hazırlık durumunu kaydederek gereksiz yeniden yama uygulamasını önler. Son tekrar derleme başarıyla tamamlandı. Commit/timestamp/build metadata nedeniyle yeniden derlenen JAR'ın SHA-256'sının aynı olacağı iddia edilmez.

Kaynak ZIP'i Leaf/Paper canonical yamaları, Esco yardımcı kodunu, build wrapper'ını, lisansları, test kaynaklarını, kullanılan benchmark JAR'ını ve ham kanıtları içerir. Minecraft JAR'ı, dünyalar, JDK, bağımlılık cache'i veya kabul edilmiş EULA içermez. Tam sunucuyu oluşturmak için Git, Python 3, JDK 25 ve internet gerekir:

```sh
python3 build-esco.py --java-home /path/to/jdk25
```

Çıktı: `dist/escos-fork-26.2-0.7.0.jar`. Tekrar ölçme komutları `verification/README.md` içindedir. Kanıtlar `verification/evidence-0.7.0/` altında; 46 koşunun tüm örnekleri `final-070/*/*/benchmark-result.json`, toplu veriler `final-070/runs.csv`, denetim `final-070/audit-result.json` dosyasındadır. `files.sha256.json` toplanan kanıt dosyalarının bütünlüğünü kaydeder. Reddedilen pilot ve yardımcı tanı ölçümleri ayrı adlarla saklanır.

Windows'ta JAR ve BAT aynı klasördeyken:

```bat
start-esco.bat --workers all
start-esco.bat --workers 4
start-esco.bat --workers auto
```

Java 25 gerekir; bu, 26.1+ Paper ailesinin gereksinimidir. [Paper Java gereksinimleri](https://docs.papermc.io/paper/getting-started/). BAT argüman doğrulaması kod üzerinden incelendi; test ortamı Linux olduğu için Windows üzerinde çalıştırılmadı. JAR Linux'ta çalıştırıldı. İlk açılış Minecraft dosyalarını indirir ve Minecraft EULA kabulü gerektirir.

`/esco` worker sınırını, fiili tepe kullanımı ve iş sayaçlarını gösterir. BAT'ın varsayılanı `auto`dur. Eski `config/escos.properties` dosyası otomatik ezilmez; geçişte `sensors.parallel-threshold=65536`, `spawning.items-per-task=16384` ve README'deki yeni ayarları kontrol edin. Testler taze ayarlarla yapıldı. BAT, Leaf async profilini otomatik değiştirmez; kendi profiliniz ölçüm profilinden farklı olabilir.
