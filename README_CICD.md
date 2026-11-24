# 🚀 Hướng Dẫn CI/CD Chuẩn Hóa Cho Hệ Thống Microservices (GitHub Container Registry - GHCR)

Tài liệu này hướng dẫn chi tiết kiến trúc, cấu hình và vận hành hệ thống **CI/CD chuẩn hóa** cho dự án **microservices-session07** sử dụng **GHCR (`ghcr.io`)**:
1. **Không build lại image trên máy chủ VPS**: Toàn bộ quá trình kiểm thử Java, đóng gói và build Docker Image được thực hiện tập trung trên GitHub Actions Runner (CI). Runner sau đó sẽ đẩy (push) image trực tiếp lên GitHub Packages / Container Registry (`ghcr.io`).
2. **Triển khai siêu tốc và tiết kiệm tài nguyên (CD)**: Máy chủ VPS chỉ kéo image đã build sẵn về (`docker compose pull <service>`) và khởi chạy (`docker compose up -d --no-build <service>`), không tốn CPU/RAM để biên dịch Java hay build container trên VPS, tránh hoàn toàn nguy cơ tràn RAM/treo máy.
3. **Tiến trình gộp CI/CD độc lập cho từng microservice**: Mỗi microservice sở hữu một pipeline riêng (`ci_cd-<service>.yml`), chỉ kích hoạt khi service đó thay đổi code.

---

## 📌 1. Danh Sách Các Microservices & Docker Images trên GHCR

Trong file [`docker-compose.yml`](file:///d:/RIKKEI/LOP%20HOC/CHUONGTRINH\DEVOPS\CI_CD\Microservices\microservices-session07\docker-compose.yml), toàn bộ dịch vụ đã được chuẩn hóa với cú pháp image: `${IMAGE_PREFIX:-ghcr.io/haibtrikkei}/<service-name>:${IMAGE_TAG:-latest}`:

| Microservice | Port | Database / Cache | Tên Docker Image trên GHCR | Workflow CI/CD |
| :--- | :--- | :--- | :--- | :--- |
| **eureka-service** | 8761 | Không | `ghcr.io/<owner>/eureka-service:latest` | [`ci_cd-eureka-service.yml`](.github/workflows/ci_cd-eureka-service.yml) |
| **config-service** | 8888 | Không | `ghcr.io/<owner>/config-service:latest` | [`ci_cd-config-service.yml`](.github/workflows/ci_cd-config-service.yml) |
| **gateway-service** | 8080 | Không | `ghcr.io/<owner>/gateway-service:latest` | [`ci_cd-gateway-service.yml`](.github/workflows/ci_cd-gateway-service.yml) |
| **user-service** | 8085 | PostgreSQL (`user_db`), Redis | `ghcr.io/<owner>/user-service:latest` | [`ci_cd-user-service.yml`](.github/workflows/ci_cd-user-service.yml) |
| **product-service** | 8086 | PostgreSQL (`product_db`) | `ghcr.io/<owner>/product-service:latest` | [`ci_cd-product-service.yml`](.github/workflows/ci_cd-product-service.yml) |
| **order-service** | 8087 | PostgreSQL (`order_db`), Redis | `ghcr.io/<owner>/order-service:latest` | [`ci_cd-order-service.yml`](.github/workflows/ci_cd-order-service.yml) |

---

## ⚙️ 2. Luồng Vận Hành Pipeline (Build ở CI -> Pull ở CD)

```
Developer Push Code vào <service>/** (vd: product-service/**)
       │
       ▼ (Chỉ kích hoạt pipeline ci_cd-product-service.yml)
[ GIAI ĐOẠN 1: CI (GitHub Actions Runner) ]
  ├── 1. Khởi tạo Database container nếu cần (Postgres:17, Redis:7)
  ├── 2. Setup JDK 21 (Eclipse Temurin) + Cache Gradle
  ├── 3. Chạy Unit & Integration Tests: ./gradlew test
  ├── 4. Đăng nhập GHCR: docker/login-action với GITHUB_TOKEN
  └── 5. Buildx Build & PUSH Docker Image lên ghcr.io:
         └── Tags: ghcr.io/<owner>/<service>:latest & :<commit-sha>
       │
       ▼ (Tự động chạy tiếp job CD khi CI thành công & trên nhánh main/master)
[ GIAI ĐOẠN 2: CD (Máy chủ VPS) ]
  ├── 1. SSH vào VPS qua appleboy/ssh-action
  ├── 2. Đồng bộ mã nguồn/docker-compose.yml: git fetch & git reset --hard
  ├── 3. Đăng nhập GHCR trên VPS (nếu có secrets.GHCR_TOKEN)
  ├── 4. KÉO IMAGE MỚI NHẤT VỀ (KHÔNG BUILD TRÊN VPS):
  │      docker compose pull <service>
  ├── 5. Khởi chạy container tức thì:
  │      docker compose up -d --no-build <service>
  │      └── (Zero-downtime cho database và các service khác)
  ├── 6. Dọn dẹp images cũ: docker image prune -f
  └── 7. Kiểm tra trạng thái: docker compose ps <service>
```

---

## 🔐 3. Cấu Hình GitHub Secrets Cần Thiết

Vào **Settings** ➔ **Secrets and variables** ➔ **Actions** trên GitHub:

| Tên Secret | Bắt buộc | Mô tả | Mặc định nếu không set |
| :--- | :---: | :--- | :--- |
| `VPS_HOST` | **Có** | IP Public của máy chủ VPS | |
| `VPS_USERNAME` | **Có** | Tên user đăng nhập SSH (thường là `ubuntu` hoặc `root`) | |
| `VPS_PASSWORD` | Tuỳ chọn | Mật khẩu tài khoản VPS (nếu dùng password) | |
| `VPS_SSH_KEY` | Tuỳ chọn | SSH Private Key (khuyên dùng, không cần password) | |
| `VPS_PORT` | Không | Cổng SSH | `22` |
| `DEPLOY_PATH` | Không | Thư mục dự án trên VPS | `/home/ubuntu/deploy-microservices-1` |
| `GHCR_TOKEN` | Tuỳ chọn | GitHub Personal Access Token (PAT) có quyền `read:packages` (dùng nếu repository hoặc package GHCR ở chế độ Private) | Không bắt buộc nếu Package public |
| `GHCR_USERNAME`| Không | Username GitHub sở hữu token | Tự động lấy `github.actor` |

---

## 🛠️ 4. Thiết Lập Quyền Cho GitHub Actions (Settings trên GitHub Repo)

Để GitHub Actions có quyền tự động đẩy image lên GitHub Container Registry:
1. Vào GitHub repo: **Settings** ➔ **Actions** ➔ **General**.
2. Cuộn xuống mục **Workflow permissions**:
   - Chọn **Read and write permissions**.
   - Bấm **Save**.
3. (Tùy chọn) Đối với Packages trên GHCR: Khi image đầu tiên được push lên, bạn có thể vào trang cá nhân GitHub ➔ **Packages** ➔ chọn package vừa tạo ➔ **Package settings** ➔ đổi sang **Public** để VPS có thể pull về mà không cần login token.
