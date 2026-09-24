Aynı son JAR ve değişmeyen test eklentisiyle Leaf / Esco 4 / Esco all=8 üzerinde ayrı JFR tanısı yapıldı: 200 ısınma, 400 ölçüm tick'i, aynı sensör yükü. Üç checksum dizisi birebir eşleşti. Bu profil kaydı açık koşular **ana tabloyla birleştirilmedi**.

| JFR kontrolü | Ortalama tick (ms) | P95 (ms) | Maksimum tick (ms) | Ölçüm GC zamanı (ms) |
|---|---:|---:|---:|---:|
| leaf | 18.94 | 27.57 | 62.24 | 42 |
| esco4 | 21.92 | 31.71 | 258.52 | 58 |
| esco_all | 20.46 | 28.40 | 150.40 | 54 |

11 saniyelik duraklama bu kontrolde tekrarlanmadı. Buna rağmen iki Esco kontrolünün ortalaması Leaf'ten yüksektir; sensör üstünlüğü iddia edilmez. Örneklenen ana thread stack'lerinde entity sorgusu ve normal entity tick maliyetleri öne çıktı. Bu ayrı kayıt, önceki duraklamanın kesin nedenini belirlemiyor. JFR stack/GC/CPU özetleri ve tüm tick örnekleri `sensor-diagnostic-070/` kanıtlarına eklendi; büyük JFR iz dosyaları pakete dahil edilmedi.
