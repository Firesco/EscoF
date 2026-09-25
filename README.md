

<p align="center">
  <img src="docs/images/escof-logo.png" alt="EscoF" width="480">
</p>


<h1 align="center">EscoF</h1>

<p align="center">
  Paper ve Spigot eklenti uyumluluğunu korumayı hedefleyen, çok çekirdek odaklı Minecraft sunucu forku.
</p>

<p align="center">
  <strong>Minecraft 26.2 · 0.7.0 Beta · Java 25</strong><br>
  Developed By Firesco
</p>

---

EscoF, Leaf ve Paper üzerine kuruludur. Seçilmiş hesaplamaları worker iş parçacıklarına dağıtarak yoğun sunucu yüklerinde işlemci kaynaklarını daha verimli kullanmayı hedefler. Eklenti olaylarını ve dünya işlemlerini bütünüyle paralel çalıştırmaz; Paper/Spigot'un senkron eklenti modeli korunur.

**Beta durumu:** Eklenti uyumluluğu ve performans, kullanılan eklentilere, ayarlara ve sunucu yüküne bağlıdır. Her koşulda Leaf veya Paper'dan hızlı olduğu iddia edilmez. Önceki sürümün ölçümleri [test raporunda](docs/TEST-RAPORU-0.7.0.md) bulunur.

## İçindekiler

- [Gereksinimler](#gereksinimler)
- [Kurulum ve ilk açılış](#kurulum-ve-ilk-açılış)
- [Worker ayarları](#worker-ayarları)
- [RAM ayarı](#ram-ayarı)
- [Yapılandırma](#yapılandırma)
- [Komutlar ve sayaçlar](#komutlar-ve-sayaçlar)
- [Eklenti kurulumu](#eklenti-kurulumu)
- [Güncelleme ve geçiş](#güncelleme-ve-geçiş)
- [Sorun giderme](#sorun-giderme)
- [Kaynaktan derleme](#kaynaktan-derleme)

## Gereksinimler

| Bileşen | Gereksinim |
| --- | --- |
| Minecraft sürümü | **26.2** |
| Java | **Java 25**; kaynak derleme için JDK 25 |
| Sunucu dosyası | `escof-26.2-0.7.0.jar` |
| İşletim sistemi | Java 25 çalıştırabilen bir sistem; hazır BAT dosyası Windows içindir |
| Bellek | Başlatıcı varsayılanı `-Xms2G -Xmx4G`; ihtiyaca ve makinenin kaynaklarına göre değiştirilebilir |

Bu depo kaynak kodunu içerir. Sunucuyu başlatmak için derlenmiş JAR gerekir; kaynak ZIP dosyası doğrudan çalıştırılamaz. Derleme adımları [aşağıdadır](#kaynaktan-derleme).

## Kurulum ve ilk açılış

1. Java sürümünü terminalde `java -version` komutuyla kontrol edin.
2. Sunucu için ayrı bir klasör oluşturun ve JAR dosyasını içine koyun. Windows kullanıyorsanız `start-escof.bat` dosyasını da aynı klasöre ekleyin.
3. Aşağıdaki komutlardan işletim sisteminize uygun olanı çalıştırın.
4. İlk açılışta oluşan `eula.txt` dosyasını açın. [Minecraft EULA](https://www.minecraft.net/eula) metnini okuyup kabul ediyorsanız `eula=true` yapın ve sunucuyu yeniden başlatın.
5. `server.properties` üzerinden sunucu ayarlarını düzenleyin. Konsolda açılışın tamamlandığını gördükten sonra Minecraft 26.2 istemcisiyle sunucu adresinize bağlanın.

**Windows — Komut İstemi:**

```bat
start-escof.bat --workers auto
```

PowerShell kullanıyorsanız komutun başına `.\` ekleyin: `.\start-escof.bat --workers auto`.

**Linux / macOS — sunucu klasöründe:**

```sh
java -Xms2G -Xmx4G -Descof.workers=auto -jar escof-26.2-0.7.0.jar --nogui
```

Windows başlatıcısı önce `JAVA_HOME` içindeki Java'yı, bu değişken yoksa sistemdeki `java` komutunu kullanır. İlk açılışta gereken sunucu bileşenlerinin indirilmesi için internet erişimi gerekir. Sunucuyu kapatırken konsola `stop` yazın.

## Worker ayarları

Worker sayısı, EscoF'un seçilmiş hesaplamalarda kullanabileceği havuz sınırıdır. Fiziksel çekirdek ayırmaz ve belirtilen sayıda worker'ın sürekli çalışacağını garanti etmez.

| Değer | Davranış |
| --- | --- |
| `auto` veya `-1` | JVM'in gördüğü mantıksal işlemci sayısından 2 çıkarır; sonuç en az 0 olur |
| `all` | JVM'in gördüğü mantıksal işlemci sayısını kullanır |
| `0` | EscoF hesaplamalarını çağıran iş parçacığında çalıştırır; sunucunun diğer iş parçacıklarını kapatmaz |
| `1`–`32767` | Worker havuzu için açık bir üst sınır belirler |

`auto` ve `all` değerleri de 32767 teknik üst sınırına tabidir. Bu sayı bir kullanım önerisi değildir. **4 çekirdek sınırı yoktur.** Örneğin JVM 8 işlemci görüyorsa `auto` 6, `all` 8 worker sınırı seçer. Hosting paketindeki çekirdek sayısı ile JVM'in gördüğü sayı aynı olmayabilir.

```bat
start-escof.bat --workers auto
start-escof.bat --workers 6
start-escof.bat --workers all
start-escof.bat --help
```

Yukarıdaki satırlar alternatif kullanımlardır; sunucuyu seçtiğiniz tek komutla başlatın. Bayrak vermezseniz başlatıcı `auto` kullanır.

**Daha fazla worker her yükte daha fazla performans sağlamaz.** Küçük işler paralelleştirme eşiklerinin altında kalabilir. Ana sunucu iş parçacığı, chunk işlemleri, ağ işleri ve eklentiler de CPU kullanır. Ayarları aynı dünya ve benzer oyuncu yükü altında, MSPT ve gecikme değerlerini karşılaştırarak değerlendirin. Worker sayısını değiştirdikten sonra sunucuyu yeniden başlatın.

## RAM ayarı

BAT dosyası varsayılan olarak `-Xms2G -Xmx4G` kullanır. `Xms` başlangıç, `Xmx` en yüksek Java heap boyutudur. İşletim sistemi ve JVM'in heap dışı kullanımı için de bellek bırakın.

Örneğin en yüksek heap boyutunu 8 GB yapmak için `start-escof.bat` içindeki Java satırında `-Xmx4G` değerini `-Xmx8G` ile değiştirin. Doğrudan çalıştırma örneği:

```sh
java -Xms2G -Xmx8G -Descof.workers=auto -jar escof-26.2-0.7.0.jar --nogui
```

Başlatıcının `--ram` bayrağı yoktur. JVM ayarları ve `-D` seçenekleri `-jar` ifadesinden önce yazılmalıdır.

## Yapılandırma

Yeni kurulumda EscoF ayarları `config/escof.properties` dosyasına yazılır. Ayarları değiştirip kaydettikten sonra sunucuyu tamamen yeniden başlatın.

Varsayılan değerler:

```properties
workers=auto
compute.caller-participates=true

activation.enabled=true
activation.min-players=8
activation.min-entities=1024
activation.verify=false

sensors.enabled=true
sensors.pipeline=true
sensors.parallel-threshold=65536

spawning.parallel-potential=true
spawning.separate-arrays=true
spawning.items-per-task=16384
spawning.parallel-threshold=32768

poi.loaded-empty-check=true
paths.linear-reconstruction=true
```

Aktivasyon eşikleri, sensör sıralama eşiği ve doğma yoğunluğu eşiği paralel hesaplamanın hangi büyüklükteki işlerde kullanılacağını etkiler. `compute.caller-participates`, çağıran iş parçacığının da hesaplamaya katılmasını sağlar. `activation.verify=true` ek doğruluk kontrolü çalıştırır ve uyumsuzlukta upstream hesaplamaya döner; kontrolün kendisi ek maliyet oluşturur.

### Ayar önceliği

Yüksekten düşüğe öncelik sırası:

1. `-Descof.<anahtar>=<değer>` Java sistem özelliği.
2. Eski adlandırmadaki `-Descos.<anahtar>=<değer>` sistem özelliği.
3. Kullanılan `.properties` dosyasındaki değer.
4. Varsayılan değer.

**BAT dosyası, bayrak verilmediğinde bile `-Descof.workers=auto` geçirir.** Bu nedenle BAT ile başlatırken dosyadaki `workers` ayarını değiştirmek tek başına yeterli değildir; `--workers` bayrağını kullanın.

Worker değerini yalnızca yapılandırma dosyasından almak için Java komutunda worker sistem özelliğini kullanmayın:

```sh
java -Xms2G -Xmx4G -jar escof-26.2-0.7.0.jar --nogui
```

`config/escof.properties` yoksa mevcut `config/escos.properties` okunur. İkisi birden varsa yalnızca yeni dosya kullanılır; dosyalar birleştirilmez. Leaf ve Paper ayarları kendi yapılandırma dosyalarında kalır; EscoF başlatıcısı bunları otomatik değiştirmez.

## Komutlar ve sayaçlar

Oyunda yönetici yetkisiyle:

```text
/escof
```

Konsolda `escof` yazın. Eski `/esco` komutu da kullanılabilir. Komut sürümü, geliştirici bilgisini ve hesaplama sayaçlarını gösterir.

| Sayaç | Anlamı |
| --- | --- |
| `configured_workers` | Yapılandırılmış worker sınırı |
| `live_workers` | O anda yaşayan EscoF worker sayısı |
| `active_workers` | O anda hesaplama yapan worker sayısı |
| `peak_parallel_workers` | Açılıştan bu yana aynı anda hesaplama yaparken görülen en yüksek worker sayısı |
| `worker_tasks` | Worker'ların yürüttüğü hesaplama görevlerinin birikimli sayısı |
| `caller_compute_tasks` | Çağıran iş parçacığında yürütülen hesaplama görevlerinin birikimli sayısı |

Boştaki sunucuda `active_workers=0` görülmesi normaldir. Sayaçlar yapılan işi gösterir; tek başlarına performans üstünlüğünün kanıtı değildir. `/escof reload` alt komutu yoktur.

## Eklenti kurulumu

1. Eklentinin Minecraft 26.2 ve kullandığı Paper/Spigot API sürümüyle uyumlu JAR dosyasını edinin.
2. Sunucuyu durdurun ve eklenti JAR'ını `plugins/` klasörüne koyun.
3. Varsa eklentinin zorunlu bağımlılıklarını da kurun, ardından sunucuyu başlatın.
4. Konsolda `plugins` komutunu ve `logs/latest.log` dosyasını kontrol edin.

Paper ve Spigot eklentileriyle uyumluluk hedeflenir; her eklenti için garanti verilmez. Özellikle NMS kullanan veya belirli sunucu sürümlerine bağımlı eklentileri ayrı bir test sunucusunda deneyin. Fabric/Forge modları, `plugins/` klasörüne koyularak çalışmaz.

## Güncelleme ve geçiş

1. Sunucuyu `stop` ile kapatın.
2. Dünya klasörlerini, eklentileri, yapılandırmaları ve mevcut JAR'ı yedekleyin.
3. Yeni sürümü önce bu yedeğin ayrı bir kopyasında deneyin.
4. JAR'ı değiştirin; dosya adı değiştiyse başlatma komutundaki adı da güncelleyin. Hazır BAT bu sürümde `escof-26.2-0.7.0.jar` adını bekler.
5. Açılış günlüklerini, eklenti yüklenmesini ve temel oyun davranışlarını kontrol edin.

Eski Esco sürümünden geçişte `escos.properties` desteği korunur. Özel Leaf `misc.rebrand.server-mod-name` veya `misc.rebrand.server-gui-name` değerleri kullanıyorsanız görünen adı bu ayarlar belirleyebilir; istediğiniz marka adına göre düzenleyin.

## Sorun giderme

| Sorun | Kontrol edilecekler |
| --- | --- |
| JAR bulunamıyor | Dosya adı ve çalışılan klasör doğru mu? Windows'ta BAT ile JAR aynı klasörde mi? |
| Java sürüm hatası | `java -version` çıktısını ve BAT'ın kullandığı `JAVA_HOME` değerini kontrol edin. Java 25 kullanın. |
| EULA nedeniyle açılmıyor | EULA'yı kabul ettiyseniz `eula.txt` içinde `eula=true` olduğunu kontrol edin. |
| Worker ayarı değişmiyor | BAT bayrağı veya başka bir `-D` seçeneği dosyadaki değeri geçersiz kılıyor olabilir. Tam yeniden başlatma gerekir. |
| Worker sayısı düşük kalıyor | İş yükü eşikleri aşmıyor olabilir. Yapılandırılmış sınırı ve anlık etkin worker sayısını ayrı değerlendirin. |
| Worker artırınca gecikme yükseliyor | Daha düşük sınırı aynı yükte karşılaştırın. Diğer sunucu iş parçacıklarının CPU kullanımını kontrol edin. |
| Eklenti yüklenmiyor | Sürüm uyumluluğunu, bağımlılıkları ve `logs/latest.log` içindeki ilk ilgili hatayı inceleyin. |
| Bellek hatası | Heap sınırını, makinenin gerçek bellek limitini ve eklenti kullanımını kontrol edin. |

Hata bildirirken EscoF sürümünü, Java sürümünü, başlatma komutunu, ilgili ayarları, eklenti listesini ve hatayı yeniden oluşturma adımlarını ekleyin. Günlükleri paylaşmadan önce şifreleri ve erişim bilgilerini kaldırın.

## Kaynaktan derleme

<details>
<summary>Geliştirici kurulumu ve derleme komutu</summary>

Git, Python 3 ve JDK 25 gerekir. Windows'ta WSL kullanılabilir. Kaynak klasöründe:

```sh
python3 build-escof.py --java-home /path/to/jdk25
```

`/path/to/jdk25` yerine JDK kurulum yolunuzu yazın. Başarılı derleme çıktısı `dist/escof-26.2-0.7.0.jar` dosyasıdır. Gradle 9.4.1 wrapper üzerinden indirilir ve SHA-256 ile doğrulanır. İlk hazırlık için internet bağlantısı gerekir.

Derleme betiği sabitlenmiş upstream'i hazırlayıp proje yamalarını uygular. Yama girdileri değiştiğinde uygulanmış kaynak düzenlemelerinizi yedekleyip temiz bir kaynak klasörü kullanın. Derleme sürecindeki worker ayarları, sunucunun `--workers` ayarından ayrıdır.

Upstream kimlikleri `escof-build.json` ve `verification/leaf.lock.json` içinde yer alır. `leaf-api`, `leaf-server` ve `dev.escos.fork` gibi iç adlar uyumluluk amacıyla korunmuştur. Doğrulama araçlarının açıklaması [verification/README.md](verification/README.md) içindedir.

</details>

## Ölçümler ve kaynaklar

- [0.7.0 test raporu](docs/TEST-RAPORU-0.7.0.md): Önceki sürümün ölçümleri, test koşulları ve sınırlamalar. Bu kayıtlar yeni derlenmiş bir EscoF JAR'ının doğrulaması değildir.
- [Paper başlangıç rehberi](https://docs.papermc.io/paper/getting-started/)
- [Paper eklenti kurulum rehberi](https://docs.papermc.io/paper/adding-plugins/)

## Lisans ve katkılar

EscoF, Leaf ve Paper çalışmalarını temel alır. Upstream lisansları ve yazar atıfları korunur. Lisans koşulları için [LICENSE.md](LICENSE.md) ve [NOTICE.md](NOTICE.md) dosyalarına; özgün Leaf açıklaması için [README.LEAF.md](README.LEAF.md) dosyasına bakın.

<p align="center"><strong>EscoF · Developed By Firesco</strong></p>
