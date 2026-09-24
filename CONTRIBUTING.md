# EscoF'a katkı

EscoF, Leaf/Paper tabanlı deneysel bir Minecraft 26.2 forkudur. Geliştirme için Git,
Python 3 ve JDK 25 gerekir. Derleme adımları README'de, ölçüm araçlarının kullanımı
`verification/README.md` dosyasındadır.

Bir değişiklik gönderirken çözdüğü sorunu, etkilediği davranışı ve doğrulama
sonuçlarını açıklayın. Test çalıştırılmadıysa bunu açıkça belirtin. Performans
iddialarını aynı donanım, dünya, ayarlar ve eklentilerle yapılan karşılaştırmalara
dayandırın; gerilemeleri de raporlayın.

Eklenti olaylarının iş parçacığı beklentilerini, dünya kaydetme davranışını ve
veri sahipliğini koruyun. Paralel işlere canlı dünya nesneleri taşımadan önce
ömür ve eşzamanlılık koşullarını değerlendirin. Küçük, odaklı değişiklikler tercih edilir.

`leaf-server/src/main/java` yardımcı kaynakları doğrudan depoda bulunur.
Minecraft ve uygulanmış Paper kaynakları hazırlık sırasında üretilir; kalıcı
NMS/Paper değişiklikleri ilgili yama girdilerinde temsil edilmelidir. Üretilmiş
kaynakları tek başına göndererek değişikliğin kalıcı olduğunu varsaymayın.
Mevcut yazar, lisans ve upstream atıflarını koruyun.
