
---

# 🚀 Media Upload Server

Máy chủ upload file hiệu năng cao chạy trên **Java 21 + Spring Boot**, thiết kế cho môi trường mạng nội bộ (LAN), tối ưu cho SSD/NVMe.

---

## 📌 Tổng quan

Media Upload Server là hệ thống web nhẹ dùng để:

* Upload một hoặc nhiều file cùng lúc
* Truyền file dung lượng lớn trong mạng LAN
* Truy cập từ điện thoại qua QR code
* Biến laptop/PC thành file server tạm thời
* Đạt tốc độ gần với tốc độ ghi thực tế của ổ đĩa

Phù hợp cho:

* Chia sẻ file nội bộ
* Thay thế USB
* Truyền media nhanh trong cùng mạng
* Thử nghiệm hệ thống I/O hiệu năng cao

---

## 🏗 Kiến trúc hệ thống

Luồng upload:

```
Client (Browser)
      ↓
HTTP multipart/form-data
      ↓
Embedded Tomcat
      ↓
Spring MultipartResolver
      ↓
Thư mục tạm (java.io.tmpdir)
      ↓
Controller
      ↓
Thư mục lưu trữ chính
```

### Đặc điểm kỹ thuật

* Sử dụng Embedded Tomcat (thread pool mặc định)
* Hiệu năng phụ thuộc chủ yếu vào Disk I/O
* Không phụ thuộc nhiều vào CPU
* Không yêu cầu database (tuỳ nhu cầu mở rộng)
* java.io.tmpdir => vùng nhớ tạm của JVM ánh xạ vào thư mục tmp của ổ địa ( copy file lớn cần lưu ý )

---

## ⚙️ Công nghệ sử dụng

* Java 21 (LTS)
* Spring Boot
* Embedded Tomcat
* HTML + JavaScript (XHR / Fetch API)
* Khuyến nghị chạy trên Linux
* Tối ưu cho SSD/NVMe

---

## 📁 Cấu trúc lưu trữ khuyến nghị

Đối với hệ thống upload file lớn, nên dùng phân vùng riêng:

```
/var/uploads/tmp
/var/uploads/storage
```

Cấu hình trong `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      location: /var/uploads/tmp
      max-file-size: -1
      max-request-size: -1
```

⚠ Không nên để thư mục tạm ở phân vùng root nhỏ, bở vỳ copy file nặng hàng trăm GB bến tạm không trụ nổi => chưa muốn fix

---

## 🚀 Hướng dẫn cài đặt

### Yêu cầu

* Linux (khuyến nghị)
* Java 17 trở lên
* Maven (hoặc dùng Maven Wrapper)

---

### Build

```bash
./mvnw clean package
```

---

### Chạy ứng dụng

```bash
java -jar target/media-upload-server.jar
```

Hoặc chạy ở chế độ phát triển:

```bash
./mvnw spring-boot:run
```

---

## 🌐 Truy cập hệ thống

Sau khi khởi động:

```
http://<LAN_IP>:<PORT>
```

Xác định IP LAN bằng:

```bash
ip addr
```

---
Dưới đây là đoạn **cảnh báo tiếng Việt ngắn gọn**, đúng trọng tâm vấn đề Docker & QR:

---

## ⚠️ Cảnh báo khi chạy bằng Docker

Khi chạy ứng dụng bên trong Docker, hệ thống có thể tự động lấy **IP của container (ví dụ: 172.x.x.x)** thay vì **IP LAN của máy host**.

Điều này dẫn đến:

* Mã QR được tạo ra chứa IP của container
* Thiết bị khác trong mạng nội bộ (LAN) sẽ **không truy cập được**
* Ứng dụng vẫn hoạt động bình thường, chỉ sai địa chỉ trong mã QR
* không sao ở ngoài tim đúng ip của host và port mặc định 8080 đã ánh xạ vào trong docker rôi

Nguyên nhân là do Docker sử dụng mạng riêng (network namespace), nên IP bên trong container không phải IP thực của máy host.

---
Dưới đây là phần bổ sung cho README về URL endpoint, viết ngắn gọn, đúng trọng tâm:

---

## 🌐 URL & Endpoint Truy Cập

Sau khi khởi động ứng dụng (Docker hoặc chạy trực tiếp), hệ thống sẽ hoạt động tại:

```
http://<IP_HOST>:8080
```

### Các endpoint chính:

* Trang chính:

```
/
```

* Trang giám sát hệ thống:

```
/monitor
```

Ví dụ truy cập:

```
http://192.168.1.50:8080/
http://192.168.1.50:8080/monitor
```

> Lưu ý: Khi chạy bằng Docker, cần sử dụng **IP LAN của máy host**, không sử dụng IP container (172.x.x.x).
> Công ánh xạ mặc định 8080 sẽ truy cập được nếu ở host.

---


## 📊 Hiệu năng thực tế

Thử nghiệm trên NVMe nội bộ:

* ~350–400 MB/s (loopback test)
* CPU ~30%
* Giới hạn chính: tốc độ ghi ổ đĩa

Hiệu năng phụ thuộc vào:

* Loại SSD
* Hệ thống file (ext4, btrfs…)
* Số lượng upload đồng thời

---

## ⚠ Hạn chế hiện tại

* Chưa hỗ trợ resumable upload
* Chưa có chunk upload API
* Chưa có xác thực người dùng
* File lớn có thể chiếm dung lượng gấp đôi tạm thời (temp + bản chính)

---

## 🔐 Lưu ý bảo mật

Không nên expose ra Internet nếu chưa có:

* Xác thực người dùng
* Giới hạn dung lượng upload
* Kiểm tra loại file
* Rate limiting
* Reverse proxy (ví dụ Nginx)

Khuyến nghị sử dụng trong mạng nội bộ tin cậy.

---

## 🧠 Hướng phát triển

* API upload theo chunk
* Resume khi mất kết nối
* WebSocket hiển thị tiến trình realtime
* Hỗ trợ nhiều người dùng
* Giới hạn quota
* Tự động dọn file tạm
* Logging & giám sát I/O
* Docker hóa hệ thống

---

## 🛠 Công cụ kiểm tra

Kiểm tra thư mục tạm của JVM:

```bash
java -XshowSettings:properties -version | grep tmp
```

Kiểm tra dung lượng ổ:

```bash
df -h
```

Theo dõi I/O:

```bash
iostat -x 1
```

---

## 📜 Giấy phép


---

## 🤝 Đóng góp

Pull request luôn được chào đón.
Vui lòng mở issue trước khi thay đổi lớn để thảo luận kiến trúc.

---

## ⭐ Mục tiêu dự án

* Không cloud
* Không USB
* Không phức tạp
* Chỉ cần tốc độ LAN + tốc độ ổ đĩa

---

