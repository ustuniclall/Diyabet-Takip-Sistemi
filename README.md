# Web Tabanlı Kural Tabanlı Diyabet Takip ve İzleme Sistemi 🩺📊

Bu proje, **Kocaeli Üniversitesi Bilgisayar Mühendisliği** Programlama Laboratuvarı-II dersi kapsamında geliştirilmiş; hastaların kan şekeri, diyet, egzersiz ve hastalık belirtilerini kayıt altına alarak doktorların bu verileri dinamik bir şekilde izlemesini, filtrelemesini ve kural tabanlı sistemlerle analiz etmesini sağlayan JavaFX tabanlı bir masaüstü otomasyonudur.

## 🚀 Projenin Amacı
Diyabet hastalarının günlük sağlık parametrelerinin takibini dijitalleştirerek doktor-hasta arasındaki koordinasyonu senkronize hale getirmektir. Geliştirilen kural tabanlı karar destek sistemi ve insülin öneri mekanizması ile riskli durumların anlık tespit edilmesi ve hastaların tedaviye uyum oranlarının grafiksel olarak raporlanması hedeflenmiştir.

## 🛠️ Teknolojik Altyapı ve Kütüphaneler
* **Arayüz Framework:** JavaFX (Zengin ve kullanıcı dostu grafiksel arayüz bileşenleri)
* **Veritabanı:** MySQL (İlişkisel veritabanı mimarisi, `PreparedStatement` ve `ResultSet` ile güvenli sorgu yönetimi)
* **E-Posta Entegrasyonu:** JavaMail API / SMTP Protokolü (Otomatik geçici şifre üretimi ve bildirim senaryoları)
* **Güvenlik ve Şifreleme:** BCrypt Algoritması (Şifrelerin veritabanına hashlenerek güvenli aktarılması)

## 🕹️ Çekirdek Sistem Fonksiyonları
1. **SMTP Tabanlı Kimlik Doğrulama:** Yeni hasta kaydı yapıldığında, JavaMail API aracılığıyla hastanın e-posta adresine sistem tarafından rastgele üretilen güvenli bir geçici şifre gönderilir.
2. **Kural Tabanlı Karar Verme (Rule-Based System):** Girilen kan şekeri değerlerine ve ölçüm sıklığına göre sistem otomatik aksiyonlar üretir:
   * *Ölçüm Eksikliği Kontrolü:* Günlük hiç veri girilmemişse -> `Ölçüm Eksik Uyarısı`.
   * *Ölçüm Yetersizliği Kontrolü:* Gün içinde 3'ten az veri girilmişse -> `Ölçüm Yetersiz Uyarısı`.
   * *Kritik Seviye Analizi:* Kan şekeri < 70 mg/dL veya > 200 mg/dL ise -> `Acil Durum Uyarısı` (Hipoglisemi / Hiperglisemi tespiti).
3. **Grafiksel Raporlama ve Analiz:** Hastanın geçmiş kan şekeri ortalamaları, günlük diyet ve egzersiz planlarına uyum yüzdeleri arayüz üzerinde dinamik sütun grafikleri (Chart) ile görselleştirilir.
4. **Doktor / Admin Paneli:** Doktorlar (Admin) sistem üzerinde hasta ekleme, kritik değerleri filtreleme, hastalara özel diyet ve egzersiz programları tanımlama yetkilerine sahiptir.

## 📊 Veritabanı İlişkisel Şeması (E/R)
Sistem mimarisi MySQL üzerinde birbiriyle ilişkili ve kısıtları tanımlanmış modüler tablolarla yönetilir:
* `bölümler` & `kullanıcılar`: Doktorların ve hastaların BCrypt ile hashlenmiş şifrelerini ve rol tanımlarını barındırır.
* `kan_sekeri`: Hastaların zaman damgalı anlık kan şekeri ölçüm verilerini tutar.
* `gunluk_takip`: Egzersiz, diyet durumları ve ek belirtilerin (`kilo kaybı`, `yorgunluk` vb.) takibini sağlar.
* `insulın_oneri`: Kural tabanlı sistemin ürettiği insülin tavsiyelerini saklar.

## 📸 Ekran Görüntüleri

| 1. Mail ile Şifre Gönderimi | 2. Hasta Kullanıcı Paneli | 3. Doktor / Admin Yönetimi |
| :---: | :---: | :---: |
| <img src="screenshots/mail_sifre.png" width="230"> | <img src="screenshots/hasta_paneli.png" width="230"> | <img src="screenshots/admin_panel.png" width="230"> |

| 4. Günlük Kan Şekeri Grafiği | 5. Diyet & Egzersiz Uyum Grafiği |
| :---: | :---: |
| <img src="screenshots/kan_sekeri_grafigi.png" width="280"> | <img src="screenshots/uyum_grafigi.png" width="280"> |

## 👥 Geliştiriciler
* **Merve Kübra ÖZTÜRK**
* **İclal ÜSTÜN**
