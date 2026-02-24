Dưới đây là nội dung **README.md chuyên nghiệp** giải thích chi tiết toàn bộ cấu hình, cách truyền biến môi trường và các lưu ý production cho dự án **ServerUpload** của bạn.

Bạn có thể copy toàn bộ nội dung này vào file `README.md`.

---

# ServerUpload

ServerUpload là một ứng dụng Spring Boot dùng để upload và quản lý file (ảnh, video, tài liệu) qua HTTP API.
Thiết kế hướng đến:

* Triển khai nội bộ (LAN)
* Chạy standalone bằng file `.jar`
* Hoặc deploy bằng Docker
* Hỗ trợ upload file lớn (GB)

---

# 1. Yêu cầu hệ thống

* Java 17+
* Linux / Windows / macOS
* Khuyến nghị: SSD nếu upload file lớn

---

# 2. Cấu trúc cấu hình

Ứng dụng sử dụng `application.yml` và cho phép override bằng **biến môi trường (ENV)**.

Cú pháp:

```yaml
property: ${ENV_NAME:default_value}
```

Nếu biến môi trường tồn tại → sử dụng giá trị ENV
Nếu không → sử dụng giá trị mặc định

---

# 3. Giải thích chi tiết cấu hình

---

## 3.1 Server Configuration

```yaml
server:
  port: ${SERVER_PORT:8080}
  address: ${SERVER_ADDRESS:0.0.0.0}
```

### SERVER_PORT

* Cổng chạy ứng dụng
* Mặc định: `8080`

Ví dụ:

```bash
export SERVER_PORT=9090
```

---

### SERVER_ADDRESS

* Địa chỉ bind
* `0.0.0.0` cho phép truy cập từ mạng nội bộ hoặc Docker
* `127.0.0.1` chỉ cho truy cập local

---

## 3.2 Tomcat Configuration

```yaml
tomcat:
  max-swallow-size: ${TOMCAT_MAX_SWALLOW_SIZE:2GB}
  max-http-form-post-size: ${TOMCAT_MAX_FORM_SIZE:10GB}
```

### TOMCAT_MAX_SWALLOW_SIZE

Giới hạn dung lượng Tomcat chấp nhận khi client hủy upload giữa chừng.

⚠ Không nên để `-1` trong production (nguy cơ DOS).

---

### TOMCAT_MAX_FORM_SIZE

Giới hạn kích thước form POST tổng thể.

Nếu upload file lớn, giá trị này phải lớn hơn `MAX_FILE_SIZE`.

---

### Thread Pool

```yaml
threads:
  max: ${TOMCAT_MAX_THREADS:100}
  min-spare: ${TOMCAT_MIN_SPARE:20}
```

| Biến               | Ý nghĩa                           |
| ------------------ | --------------------------------- |
| TOMCAT_MAX_THREADS | Số request tối đa xử lý đồng thời |
| TOMCAT_MIN_SPARE   | Số thread duy trì sẵn             |

⚠ Upload file lớn giữ connection lâu → không nên để quá thấp.

---

## 3.3 Application Custom Config

```yaml
app:
  upload-dir: ${UPLOAD_DIR:/var/upload}
  max-parallel-upload: ${MAX_PARALLEL_UPLOAD:10}
  allowed-types: ${ALLOWED_TYPES:image/*,video/*,application/pdf}
```

### UPLOAD_DIR

Thư mục lưu file upload.

Phải đảm bảo có quyền ghi:

```bash
sudo mkdir -p /var/upload
sudo chown -R user:user /var/upload
```

Nếu dùng Docker:

```bash
-v /data/upload:/var/upload
```

---

### MAX_PARALLEL_UPLOAD

Giới hạn số upload song song (xử lý phía service nếu có logic).

Giúp tránh quá tải IO disk.

---

### ALLOWED_TYPES

Danh sách MIME type được phép upload.

Nên validate lại ở backend để tránh upload file độc hại.

---

## 3.4 Spring Multipart Configuration

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${MAX_FILE_SIZE:5GB}
      max-request-size: ${MAX_REQUEST_SIZE:10GB}
      file-size-threshold: ${FILE_THRESHOLD:2KB}
```

### MAX_FILE_SIZE

Kích thước tối đa mỗi file.

### MAX_REQUEST_SIZE

Tổng kích thước tối đa một request.

### FILE_THRESHOLD

Ngưỡng ghi file xuống disk.

Khuyến nghị:

* Giữ nhỏ (2KB–1MB)
* Tránh giữ file lớn trong RAM

---

## 3.5 Logging

```yaml
logging:
  level:
    root: ${LOG_LEVEL:INFO}
  file:
    name: ${LOG_FILE:logs/serverupload.log}
```

### LOG_LEVEL

* DEBUG
* INFO
* WARN
* ERROR

Production nên để INFO hoặc WARN.

---

# 4. Cách chạy ứng dụng

---

## 4.1 Chạy mặc định

```bash
java -jar serverupload.jar
```

---

## 4.2 Truyền biến môi trường Linux

```bash
export SERVER_PORT=9090
export UPLOAD_DIR=/data/upload
export MAX_FILE_SIZE=10GB

java -jar serverupload.jar
```

Hoặc:

```bash
SERVER_PORT=9090 UPLOAD_DIR=/data/upload java -jar serverupload.jar
```

---

## 4.3 Chạy bằng Docker

```bash
docker run -d \
  -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e UPLOAD_DIR=/data/upload \
  -v /data/upload:/data/upload \
  serverupload
```

---

# 5. Khuyến nghị Production

---

## Không để unlimited

Không dùng:

```yaml
-1
```

Nguy cơ:

* DOS attack
* Treo JVM
* Full RAM
* Full disk

---

## Nếu server RAM 2GB–4GB

Giảm:

```bash
TOMCAT_MAX_THREADS=50
MAX_PARALLEL_UPLOAD=5
```

---

## Nếu upload file 5GB+

Khuyến nghị:

* Dùng SSD
* Theo dõi IO:

```bash
iostat -x 1
```

---

## Giới hạn tài nguyên Docker

```bash
--memory="2g"
--cpus="2"
```

---

# 6. Bảo mật

Khuyến nghị bổ sung:

* Authentication
* Rate limiting
* Virus scan (ClamAV)
* Giới hạn extension file
* Reverse proxy (Nginx) nếu public internet

---

# 7. Mở rộng tương lai

Có thể nâng cấp:

* Chuyển sang lưu object storage (MinIO / S3)
* Tách thành upload service riêng
* Thêm checksum SHA-256
* Thêm resumable upload (chunk upload)

---

# 8. Tổng kết

ServerUpload hỗ trợ:

* Upload file lớn
* Cấu hình linh hoạt qua ENV
* Phù hợp nội bộ hoặc container
* Dễ scale khi cần

---
