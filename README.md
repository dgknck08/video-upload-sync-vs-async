# Video Processor Project

Bu proje, video yükleme ve işleme işlemlerini **senkron** ve **asenkron** yöntemlerle gerçekleştiren bir servis uygulamasıdır. Ayrıca, mesaj kuyruğu olarak **RabbitMQ (AMQP)** kullanmaktadır.

---

## Teknolojiler

- Java 17  
- Spring Boot 3.5.3  
- RabbitMQ (AMQP)  
- PostgreSQL 15  
- Docker & Docker Compose  
- FFmpeg (video işleme için)  

---

## Proje Özellikleri

- **Video Yükleme:** Kullanıcılar video dosyalarını yükler.  
- **Video İşleme:** Yüklenen videolar FFmpeg ile thumbnail oluşturma, transcoding ve metadata çıkarma işlemlerinden geçer.  
- **Senkron İşleme:** İstek yapan client, işlemin tamamlanmasını bekler.  
- **Asenkron İşleme:** Video işleme görevleri RabbitMQ kuyruğuna atılır ve arka planda işlenir.  
- **Veri Depolama:** Videolar ve işlem durumu PostgreSQL veritabanında tutulur.  

---

## Senkron (Synchronous) vs Asenkron (Asynchronous) Video İşleme

| Özellik                        | Senkron İşleme                            | Asenkron İşleme                        |
|--------------------------------|-------------------------------------------|----------------------------------------|
| İşlem Süresi                   | Uzun sürebilir, client bekler             | Hızlı cevap verir, işlem arka planda   |
| Kaynak Kullanımı               | Sunucu kaynakları işlem boyunca bloklanır | Kaynaklar daha verimli kullanılır      |
| Ölçeklenebilirlik              | Düşük                                     | Yüksek                                 |
| Hata Yönetimi                  | Hatalar anında görülür                    | Hatalar kuyruğa göre yönetilir         |
| Kullanım Senaryosu             | Küçük dosyalar, hızlı işlemler            | Büyük dosyalar, yüksek hacimli işlemler|

---

## RabbitMQ (AMQP) Kullanımı

RabbitMQ, mesajların güvenli ve verimli bir şekilde işlenmesi için kullanılan bir mesaj kuyruğudur.

- **Mesaj Kuyruğu:** Video işleme istekleri RabbitMQ kuyruğuna gönderilir.  
 
- **Yönetim Paneli:** `http://localhost:15672` üzerinden erişilebilir. (Default kullanıcı: guest / guest)  

---

## Docker ve Docker Com
