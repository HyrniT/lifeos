# Triển khai LifeOS trên các gói miễn phí

Toàn bộ hệ thống chạy được với **0 đồng/tháng**, trên ba dịch vụ:

| Thành phần | Nền tảng | Gói | Giới hạn thật sự |
|---|---|---|---|
| Cơ sở dữ liệu | **Neon** | Free | 0.5 GB lưu trữ, tự ngủ sau 5 phút, ~500ms để tỉnh |
| Backend (1 service Java) | **Render** | Free | 512 MB RAM, 0.1 CPU, **dừng hẳn sau 15 phút không có request** |
| Web app | **Vercel** | Hobby | 100 GB băng thông/tháng, không ngủ |
| CI + giữ ấm | **GitHub Actions** | Free | Không giới hạn phút với repo public |

Ước tính khoảng **35–50 phút** cho lần đầu, phần lớn là chờ build.

> **Điều bạn nên biết trước khi bắt đầu.** Gói free của Render *dừng* instance sau
> 15 phút không có traffic. Request kế tiếp phải chờ JVM khởi động lại — khoảng
> **40–60 giây**. Workflow `keep-alive.yml` trong repo này ping mỗi 10 phút để
> tránh phần lớn tình huống đó, nhưng không xoá bỏ được nó. Nếu bạn cần "luôn
> luôn ấm" thật sự thì đó là gói $7/tháng của Render — không có mẹo nào thay thế
> được, và ai nói ngược lại là đang bán cho bạn một thứ không tồn tại.

---

## Trước khi bắt đầu

Bạn cần: một tài khoản GitHub, và repo này đã được push lên đó.

```bash
cd lifeos-mono
git init
git add .
git commit -m "LifeOS as a single service"
git branch -M main
git remote add origin https://github.com/<tên-bạn>/lifeos.git
git push -u origin main
```

**Để repo ở chế độ public.** Không phải vì mã nguồn, mà vì GitHub Actions không
tính phút cho repo public — workflow giữ ấm chạy mỗi 10 phút sẽ vượt hạn mức
2.000 phút/tháng của repo private. Không có bí mật nào nằm trong repo; tất cả
đều là biến môi trường ở phía Render và Vercel.

Sinh sẵn hai giá trị, bạn sẽ cần ở Bước 2 (giữ lại trong một file tạm):

```bash
# Khoá ký JWT. Bắt buộc ≥ 64 byte — ứng dụng từ chối khởi động nếu ngắn hơn.
openssl rand -base64 96

# Mật khẩu quản trị viên
openssl rand -base64 18
```

Không có `openssl` trên Windows? Dùng Git Bash (đã cài kèm Git), hoặc:

```powershell
[Convert]::ToBase64String((1..72 | ForEach-Object { Get-Random -Max 256 }))
```

---

## Bước 1 — Cơ sở dữ liệu trên Neon

1. Vào <https://neon.tech> → **Sign up** bằng GitHub.
2. **Create project**:
   - Name: `lifeos`
   - Postgres version: **16**
   - Region: **AWS ap-southeast-1 (Singapore)** — gần Việt Nam nhất, và phải
     cùng khu vực với Render ở Bước 2, nếu không mỗi truy vấn phải đi vòng nửa
     vòng trái đất.
3. Neon hiện ra một connection string. Bấm **Show password** và copy toàn bộ:

   ```
   postgresql://lifeos_owner:npg_AbC123xyz@ep-cool-forest-a1b2c3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```

4. **Tách nó ra làm ba phần** — Render cần ba biến riêng, và JDBC dùng cú pháp
   khác với `psql`:

   | Biến | Giá trị lấy từ chuỗi trên |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-forest-a1b2c3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DATABASE_USERNAME` | `lifeos_owner` |
   | `DATABASE_PASSWORD` | `npg_AbC123xyz` |

   Ba điểm dễ sai, và cả ba đều thất bại theo cách khó đoán:
   - Thêm tiền tố `jdbc:` — thiếu nó, driver không nhận ra chuỗi.
   - **Bỏ `user:password@` khỏi URL.** Để lại thì driver đọc user từ URL còn
     Spring đọc từ biến, và khi hai bên khác nhau bạn sẽ nhận lỗi xác thực với
     một tên người dùng bạn không hề gõ.
   - **Giữ `?sslmode=require`.** Neon từ chối kết nối không mã hoá, và thông báo
     lỗi không nói gì về SSL.

5. Không cần tạo schema hay bảng gì cả. Ứng dụng tự tạo sáu schema
   (`auth`, `habit`, `expense`, `planning`, `notification`, `analytics`) trong
   lần khởi động đầu tiên.

---

## Bước 2 — Backend trên Render

1. Vào <https://render.com> → **Sign up** bằng GitHub (gói free không cần thẻ).
2. **New** → **Blueprint** → chọn repo vừa push.
   Render đọc `render.yaml` ở thư mục gốc và tự cấu hình mọi thứ trừ các biến bí mật.
3. Render hỏi từng biến một. Điền:

   | Biến | Giá trị |
   |---|---|
   | `DATABASE_URL` | chuỗi JDBC ở Bước 1 |
   | `DATABASE_USERNAME` | user ở Bước 1 |
   | `DATABASE_PASSWORD` | password ở Bước 1 |
   | `ADMIN_PASSWORD` | chuỗi ngẫu nhiên bạn đã sinh |
   | `LIFEOS_CORS_ORIGINS` | *tạm để* `http://localhost:5273` — sẽ sửa ở Bước 4 |
   | `PUBLIC_URL` | *tạm để trống* — sẽ điền ở Bước 4 |

   `JWT_SECRET` để Render tự sinh (`generateValue: true` trong blueprint).
   Các biến còn lại (`VAPID_*`, `GOOGLE_*`) là tuỳ chọn — bỏ trống, xem
   [Tuỳ chọn](#tuỳ-chọn) bên dưới.

4. **Apply**. Lần build đầu mất **8–12 phút**: Render dựng image Docker,
   Maven tải toàn bộ dependency. Các lần sau nhanh hơn nhiều nhờ layer cache.

5. Khi log hiện `Your service is live`, kiểm tra:

   ```bash
   curl https://lifeos-xxxx.onrender.com/actuator/health
   # {"status":"UP","groups":["liveness","readiness"]}
   ```

   Ghi lại URL này. Trong log khởi động bạn sẽ thấy sáu dòng migration:

   ```
   Schema 'auth': 1 migration(s) applied, now at version 1
   Schema 'habit': 2 migration(s) applied, now at version 2
   ...
   ```

   Đó là bằng chứng database đã sẵn sàng.

6. Quay lại **Environment**, điền `PUBLIC_URL` = chính URL đó. Save.

---

## Bước 3 — Web app trên Vercel

1. Vào <https://vercel.com> → **Sign up** bằng GitHub.
2. **Add New** → **Project** → chọn repo.
3. Cấu hình — **phần Root Directory là chỗ hay bị bỏ sót**:

   | Trường | Giá trị |
   |---|---|
   | Framework Preset | **Vite** |
   | **Root Directory** | **`frontend`** ← bấm *Edit* và chọn |
   | Build Command | `npm run build` (mặc định) |
   | Output Directory | `dist` (mặc định) |

4. Mở **Environment Variables**, thêm một biến:

   | Name | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://lifeos-xxxx.onrender.com/api` |

   Đúng URL Render ở Bước 2, **có `/api` ở cuối, không có dấu `/` thừa**.

   Vite nhúng biến này vào bundle **lúc build**, không đọc lúc chạy. Nghĩa là
   sửa biến này về sau thì phải **Redeploy** mới có tác dụng — sửa xong mà không
   deploy lại là một trong những cách mất thời gian phổ biến nhất ở bước này.

5. **Deploy**. Khoảng 1–2 phút. Vercel cho bạn một URL dạng
   `https://lifeos-xxxx.vercel.app`.

---

## Bước 4 — Nối hai đầu lại

Web app bây giờ đã gọi được backend, nhưng trình duyệt sẽ chặn phản hồi vì
backend chưa biết origin đó. Sửa nốt:

1. Render → service `lifeos` → **Environment**:

   | Biến | Giá trị |
   |---|---|
   | `LIFEOS_CORS_ORIGINS` | `https://lifeos-xxxx.vercel.app` |

   **Không có dấu `/` ở cuối.** So khớp origin là so chuỗi chính xác;
   `https://x.vercel.app/` sẽ không khớp với `https://x.vercel.app` và bạn sẽ
   thấy lỗi CORS trong khi `curl` vẫn chạy tốt — triệu chứng gây hoang mang nhất
   của bước này.

   Muốn cho phép cả các bản preview của Vercel thì thêm mẫu, cách nhau bởi dấu phẩy:

   ```
   https://lifeos-xxxx.vercel.app,https://lifeos-*-<tên-bạn>.vercel.app
   ```

2. **Save, rebuild** — Render tự khởi động lại.

3. Mở `https://lifeos-xxxx.vercel.app`, đăng nhập bằng `admin` và
   `ADMIN_PASSWORD` bạn đã đặt.

   **Lần đăng nhập đầu tiên sẽ chậm** nếu instance đang ngủ. Chờ khoảng một phút.

4. **Đổi mật khẩu admin trong Settings, rồi tắt seed**: thêm biến
   `ADMIN_SEED_ENABLED=false` trên Render. Tài khoản seed chỉ để đăng nhập lần
   đầu; để nguyên nghĩa là mật khẩu đó nằm trong biến môi trường mãi mãi.

---

## Bước 5 — CI và giữ ấm

`.github/workflows/ci.yml` đã chạy tự động ở mỗi push: test backend trên
Postgres thật, typecheck + build frontend, và build luôn image Docker mà Render
sẽ dựng — để một Dockerfile hỏng bị phát hiện trong CI chứ không phải giữa lúc deploy.

Bật giữ ấm:

1. GitHub → repo → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret**:
   - Name: `HEALTH_URL`
   - Value: `https://lifeos-xxxx.onrender.com/actuator/health`
3. **Actions** → **Keep alive** → **Run workflow** để chạy thử ngay.

Nếu repo của bạn là **private**, hãy sửa `cron` trong `keep-alive.yml` thành
`*/30 * * * *` hoặc xoá workflow — mỗi lần chạy bị tính tròn một phút, và ở tần
suất 10 phút bạn sẽ tiêu hết hạn mức 2.000 phút/tháng.

GitHub tự tắt scheduled workflow sau 60 ngày không có commit nào. Nếu một hôm
thấy app lại ngủ, đây là chỗ cần kiểm tra đầu tiên.

---

## Tuỳ chọn

### Thông báo đẩy trên trình duyệt

Không có khoá thì thông báo trong ứng dụng vẫn hoạt động bình thường; chỉ mất
phần đẩy khi đóng tab.

```bash
npx web-push generate-vapid-keys
```

Thêm vào Render: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`,
`VAPID_SUBJECT=mailto:ban@example.com`.

**Sinh một lần rồi giữ nguyên.** Đổi public key sẽ vô hiệu hoá âm thầm mọi
subscription trình duyệt đã đăng ký — người dùng không thấy lỗi, chỉ là không
bao giờ nhận được thông báo nữa.

### Đăng nhập bằng Google

1. <https://console.cloud.google.com> → **APIs & Services** → **Credentials**
2. **Create Credentials** → **OAuth client ID** → **Web application**
3. Authorized redirect URIs: `https://lifeos-xxxx.vercel.app/auth/google/callback`
4. Thêm vào Render: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, và
   `GOOGLE_REDIRECT_URI` = đúng URI ở trên.

Bỏ trống thì nút Google tự ẩn.

### Tên miền riêng

Vercel → **Settings** → **Domains** → thêm domain, trỏ DNS theo hướng dẫn.
Sau đó **nhớ cập nhật `LIFEOS_CORS_ORIGINS`** trên Render sang domain mới, nếu
không app sẽ hỏng đúng vào lúc bạn nghĩ mình vừa làm xong.

---

## Khi có gì đó hỏng

**Render deploy fail: `Cannot invoke ... because "url" is null`**
`DATABASE_URL` thiếu tiền tố `jdbc:`.

**Log có `password authentication failed for user "lifeos_owner"`**
Bạn để nguyên `user:password@` trong `DATABASE_URL`. Xoá phần đó đi — user và
password là hai biến riêng.

**Log có `The server does not support SSL`, hoặc kết nối bị đóng ngay**
Thiếu `?sslmode=require` ở cuối `DATABASE_URL`.

**Ứng dụng không khởi động: `JWT_SECRET is still the documented default`**
Đúng như nó nói. Đây là kiểm tra cố ý — với khoá mặc định, bất kỳ ai đọc repo
này đều ký được token cho tài khoản bất kỳ.

**Trình duyệt báo lỗi CORS nhưng `curl` vẫn chạy**
Đây gần như luôn là `LIFEOS_CORS_ORIGINS` sai. Kiểm tra: không có `/` ở cuối,
đúng `https`, đúng subdomain. Kiểm tra nhanh:

```bash
curl -i -X OPTIONS https://lifeos-xxxx.onrender.com/api/auth/login \
  -H "Origin: https://lifeos-xxxx.vercel.app" \
  -H "Access-Control-Request-Method: POST"
```

Phải thấy `Access-Control-Allow-Origin` đúng bằng origin bạn gửi. Không thấy
nghĩa là biến chưa đúng, hoặc Render chưa restart sau khi bạn sửa.

**Frontend gọi vào `https://lifeos-xxxx.vercel.app/api/...` thay vì Render**
`VITE_API_BASE_URL` chưa được nhúng. Vite đọc biến lúc build — thêm biến rồi
phải **Redeploy** trên Vercel.

**Request đầu tiên mất ~50 giây rồi sau đó nhanh**
Đúng như thiết kế của gói free. Xem [Bước 5](#bước-5--ci-và-giữ-ấm).

**Render OOM / container bị kill**
512 MB là toàn bộ instance. `JAVA_TOOL_OPTIONS` trong Dockerfile đã đặt heap ở
70% và dùng SerialGC cho vừa. Nếu bạn thêm dependency nặng, đây là chỗ cần chỉnh
đầu tiên — đừng nâng `MaxRAMPercentage` lên quá 75, phần còn lại là metaspace,
thread stack và bộ đệm của chính JVM.

**Neon: `too many connections`**
`DATABASE_POOL_SIZE` mặc định là 10 và vừa vặn với gói free. Nếu bạn chạy song
song một instance local trỏ vào cùng database thì giảm xuống 5.

---

## Chạy tại máy

Không cần tài khoản nào cả.

```bash
# 1. Postgres (cổng 55432, không đụng Postgres sẵn có ở 5432)
docker compose up -d

# 2. Backend — http://localhost:9080
cd backend && mvn spring-boot:run

# 3. Web app — http://localhost:5273
cd frontend && npm install && npm run dev
```

Đăng nhập `admin` / `admin`. API docs ở <http://localhost:9080/swagger-ui.html>.

Không có Docker? Bất kỳ Postgres 14+ nào cũng được — tạo database tên `lifeos`
rồi trỏ `DATABASE_URL` vào đó. Ứng dụng tự tạo schema.

Chạy test:

```bash
cd backend && mvn verify
```

Bộ test tự khởi động một Postgres thật (`io.zonky.test:embedded-postgres`) — không
cần Docker, không cần database có sẵn.

---

## Chi phí thật khi vượt gói free

Không có gì bất ngờ ở đây, nhưng cũng không có gì miễn phí mãi mãi:

| Khi nào | Chi phí |
|---|---|
| Muốn backend không bao giờ ngủ | Render Starter, **$7/tháng** |
| Database vượt 0.5 GB | Neon Launch, **$19/tháng** |
| Vercel vượt 100 GB băng thông | Pro, **$20/tháng** |

Với vài người dùng, cấu hình miễn phí ở trên chạy được vô thời hạn. Giới hạn
thực tế bạn sẽ chạm đầu tiên gần như chắc chắn là cơn khó chịu vì cold start,
chứ không phải dung lượng.
