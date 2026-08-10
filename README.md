# LifeOS

Thói quen, tiền bạc và kế hoạch trong một chỗ — cộng thêm phần phân tích liên miền
mà không màn hình đơn lẻ nào cho bạn được.

**Một service Spring Boot, một database Postgres, hai ứng dụng web.**
Chạy hết trên các gói miễn phí: [Neon](https://neon.tech) + [Render](https://render.com) +
[Vercel](https://vercel.com). Xem [DEPLOYMENT.md](DEPLOYMENT.md).

```
trình duyệt ──▶ Vercel (SPA)  ──HTTPS──▶  Render (1 service Java)  ──▶  Neon (1 Postgres)
```

Không có message broker, không service registry, không gateway, không cache
server, không load balancer. Với vài người dùng thì tất cả những thứ đó chỉ là
thêm chỗ để hỏng.

---

## Mục lục

- [Ứng dụng làm được gì](#ứng-dụng-làm-được-gì)
- [Kiến trúc](#kiến-trúc)
- [Những gì đã bị bỏ đi, và thay bằng gì](#những-gì-đã-bị-bỏ-đi-và-thay-bằng-gì)
- [Các mẫu thiết kế còn giữ lại](#các-mẫu-thiết-kế-còn-giữ-lại)
- [Bảo mật](#bảo-mật)
- [Chạy trên máy của bạn](#chạy-trên-máy-của-bạn)
- [Kiểm chứng](#kiểm-chứng)
- [Cổng dịch vụ](#cổng-dịch-vụ)
- [Bố cục thư mục](#bố-cục-thư-mục)
- [Giới hạn đã biết](#giới-hạn-đã-biết)

---

## Ứng dụng làm được gì

Không có tính năng nào bị cắt khi gộp về một service. Sáu miền nghiệp vụ vẫn
nguyên vẹn, chỉ là chúng nằm trong cùng một tiến trình.

### Thói quen (Habits)

Xây dựng hoặc từ bỏ, với năm kiểu lịch (hằng ngày, các thứ được chọn, N lần/tuần,
N lần/tháng, mỗi N ngày). Chuỗi (streak) được tính riêng theo từng kiểu, nên bỏ
Chủ nhật với một thói quen Hai/Tư/Sáu thì **không** làm đứt chuỗi. Có XP, cấp độ,
xu, HP, thẻ đóng băng chuỗi, 18 thành tựu, bản đồ nhiệt đóng góp, hồ sơ theo thứ
trong tuần và nhận định xu hướng cho từng thói quen.

### Tiền bạc (Money)

Tài khoản (tiền mặt, ngân hàng, thẻ, ví điện tử, tiết kiệm, đầu tư, khoản vay),
danh mục, giao dịch, chuyển khoản, ngân sách và quy tắc lặp lại. Số dư được cập
nhật tăng dần, tiền dùng `NUMERIC(19,4)` xuyên suốt, chuyển khoản **không bao giờ**
bị tính là thu hay chi. Thống kê gồm dòng tiền, phân bổ theo danh mục, xu hướng 12
tháng, mẫu chi theo thứ, cửa hàng chi nhiều nhất, tỷ lệ tiết kiệm, dự phóng cuối
tháng và một bộ máy nhận định theo luật. Ngân sách báo **mức chi an toàn mỗi ngày**,
không chỉ là phần trăm.

### Kế hoạch (Planning)

Công việc có độ ưu tiên, ngày đến hạn và ngày dự định làm, công việc con, lặp lại,
nhãn và ô Eisenhower được suy ra; **dự án** gom công việc lại, có màn hình riêng ở
`/projects` để tạo, đổi trạng thái và xoá — **tiến độ đếm từ chính các công việc bên
trong chứ không nhập tay**, và không xoá được chừng nào còn việc chưa xong; bấm vào
số công việc của một dự án sẽ mở `/planning?project=<id>`, danh sách được lọc ở phía
máy chủ, mở thẳng ở chế độ **All open** (việc của một dự án trải dài nhiều tuần, mở
ở "Today" thì cú bấm vào "3/6 tasks done" trả về màn hình trống), và bộ lọc nằm
trong URL nên chia sẻ và bấm back đều đúng; mục tiêu
có giá trị đích, hạn chót và nhận định sớm/trễ tiến độ; phiên pomodoro và deep work;
nhật ký hằng ngày có tâm trạng và năng lượng.

### Phân tích (Analytics)

Một dòng thời gian cho cả ba miền, biểu đồ radar cân bằng cuộc sống 5 trục, và các
tương quan tính từ chính dữ liệu của bạn (mức đều đặn thói quen ↔ chi tiêu, thời
gian tập trung ↔ đầu ra, tâm trạng ↔ thói quen). Tương quan luôn kèm hệ số và cỡ
mẫu, và được mô tả là **mẫu hình chứ không phải nhân quả**.

### Nhắc nhở & Thông báo

Phần chạm tới bạn ngay cả khi ứng dụng **không** mở.

| Thông báo | Khi nào |
|---|---|
| **Task sắp đến hạn** | tại mỗi mốc bạn chọn — 1 tuần, 3 ngày, 1 ngày, 8 giờ, 2 giờ, 1 giờ, 30 phút, 15 phút |
| **Task đến hạn** | đúng thời điểm hạn chót |
| **Task quá hạn** | mỗi ngày một lần lúc 09:00, tối đa một tuần rồi ngừng làm phiền |
| **Nhắc thói quen** | đúng giờ bạn đặt cho thói quen đó, chỉ khi hôm nay còn dở |
| **Chuỗi sắp đứt** | 21:00, nếu một chuỗi từ 3 ngày trở lên sắp mất |
| **Hạn chót mục tiêu** | trước 7 ngày và 1 ngày, kèm phần còn thiếu |
| **Tóm tắt buổi sáng** | một bản tin việc đến hạn — im lặng nếu hôm đó rảnh |
| **Cảnh báo / vượt ngân sách** | khi ngân sách gần chạm hoặc vượt mức |
| **Cột mốc chuỗi, thành tựu, lên cấp** | ngay khi xảy ra |

**Đến với bạn bằng cách nào**

- **Trong ứng dụng** — toast và chuông, qua Server-Sent Events, khi còn mở tab.
- **Web Push** — tới thiết bị, **khi ứng dụng đã đóng**. Bật riêng cho từng thiết
  bị ở *Settings → Notifications*; cần HTTPS, và trên iOS thì trang phải được
  **Thêm vào Màn hình chính** trước.

**Làm phiền bạn tới mức nào**

- Cả mười hai loại đều tắt/bật được riêng lẻ.
- **Giờ yên lặng** (mặc định 22:00–07:00) **hoãn chứ không vứt** — một lời nhắc
  đáng lẽ tới lúc 02:00 sẽ tới lúc 07:00. Việc thực sự gấp vẫn đi qua.
- Mọi lịch đều chạy theo **múi giờ của bạn**, không phải của server.

### Admin console

Một ứng dụng riêng tại `/admin/`: số liệu nền tảng, quản lý người dùng (bật/tắt,
phân quyền, buộc đăng xuất), nhật ký kiểm toán bảo mật, danh sách các module đang
phục vụ và thăm dò sức khoẻ phụ thuộc.

---

## Kiến trúc

```
                         ┌─────────────────────────┐
   trình duyệt ─────────▶│   Vercel  (tĩnh)        │   /       ứng dụng người dùng
                         │   frontend/dist         │   /admin/ admin console
                         └────────────┬────────────┘
                                      │  HTTPS, CORS
                                      ▼
              ┌───────────────────────────────────────────────┐
              │   Render — MỘT tiến trình Spring Boot          │
              │                                               │
              │   auth ─┐                                     │
              │   habit ─┤                                     │
              │   expense ┼─── bus sự kiện trong tiến trình ───┤
              │   planning┤     (ApplicationEventPublisher)    │
              │   analytics◀┘                                  │
              │   notification◀                                │
              └───────────────────────┬───────────────────────┘
                                      │  JDBC, SSL
                                      ▼
              ┌───────────────────────────────────────────────┐
              │   Neon — MỘT database, sáu schema              │
              │   auth · habit · expense · planning ·         │
              │   notification · analytics                    │
              └───────────────────────────────────────────────┘
```

**Vì sao là schema chứ không phải đổi tên bảng.** Ba miền — habit, planning,
notification — mỗi miền có một bảng tên `user_settings`. Khi mỗi service có
database riêng thì điều đó không ai để ý. Gộp vào một database, schema riêng cho
mỗi miền giữ nguyên sự thật đó, nên **mọi file migration giống hệt bản cũ đến từng
byte** và ranh giới giữa các miền vẫn nhìn thấy được trong database. Mỗi schema có
`flyway_schema_history` riêng.

---

## Những gì đã bị bỏ đi, và thay bằng gì

| Trước | Sau | Vì sao |
|---|---|---|
| Kafka (5 topic) | `ApplicationEventPublisher` + outbox | Một tiến trình thì không có mạng để mất tin. Handler vẫn là handler, chỉ đổi từ `@KafkaListener` sang `@EventListener`. |
| RabbitMQ (queue + DLQ) | `ReminderBus` | Dedupe key ở phía tiêu thụ — chứ không phải broker — mới là thứ khiến tin lặp vô hại. |
| MongoDB (analytics) | Bảng Postgres | Read model chỉ cần một dòng mỗi người mỗi ngày. Postgres giữ được hình dạng đó; chỉ map theo danh mục là phải tách thành bảng con. |
| Redis (cache + rate limit + state) | `EphemeralStore` trong bộ nhớ | Tất cả đều là dữ liệu sống 5–15 phút: challenge 2FA, state PKCE, bộ đếm đăng nhập sai. Không đáng một hạ tầng thứ hai. |
| Eureka + Spring Cloud Gateway + nginx | Không có gì | Không còn gì để tìm hay để định tuyến tới. |
| `@Cacheable` / `@CacheEvict` | Đã bỏ | Với lượng dữ liệu của một người, truy vấn rẻ hơn việc phải nghĩ về invalidation. |
| Resilience4j, Prometheus | Đã bỏ | Circuit breaker bảo vệ lời gọi mạng — không còn lời gọi nào. Không có gì scrape metrics. |
| 6 database Postgres | 1 database, 6 schema | Gói free của Neon cho một database. |
| 9 Maven module | 1 | |

**Những gì *không* bị bỏ:** event sourcing của habit, transactional outbox, mọi
API endpoint, mọi màn hình, 2FA, Google OAuth, web push, admin console, kiểm toán.

---

## Các mẫu thiết kế còn giữ lại

**CQRS + Event Sourcing (miền habit).** Mọi thay đổi thói quen đều được ghi thêm
vào `habit.event_store`; các bảng mà giao diện đọc chỉ là projection, có thể xoá và
dựng lại (`POST /api/habits/projections/rebuild` làm đúng việc đó). Thói quen là
miền duy nhất mà *lịch sử chính là sản phẩm* — "tôi đã làm gì và khi nào" mới là
toàn bộ câu hỏi — nên một write model làm từ các sự kiện là lựa chọn thật sự phù
hợp, không phải trang trí.

**Transactional Outbox.** Trước đây nó chống lại ghi đôi giữa Postgres và Kafka.
Bây giờ nó còn đáng giá hơn: không còn broker để phát lại, nên khi một handler
hỏng, chỗ để thử lại chính là bảng này. Relay chạy mỗi giây, và dừng thử lại một
dòng sau `max-attempts` để dòng đó nằm lại cho người vận hành xem.

**Idempotency bằng unique index, không bằng cờ.** Scheduler nhắc nhở chạy theo chu
kỳ và phát lại cửa sổ sau khi khởi động lại — nên cùng một lời nhắc *cố tình* được
sinh nhiều lần. Mỗi thông điệp mang dedupe key (`task:<id>:lead:120`) có unique
index đứng sau, bản chèn thứ hai thua. Cách ngây thơ — một cờ `reminder_sent` trên
task — thậm chí không diễn đạt nổi "một lời nhắc cho mỗi mốc lead time". Bên sản
xuất cố tình để ngốc; tính đúng đắn nằm ở một index.

**Tuỳ chọn nằm ở bên tiêu thụ.** Scheduler phát ra một ứng viên ở *mọi* mốc lead
time hệ thống hỗ trợ; miền notification loại bỏ những mốc người dùng không chọn.
Nhờ vậy bảng tuỳ chọn chỉ tồn tại một bản.

**Giờ địa phương, ở mọi nơi.** Hạn chót là sự kiện theo đồng hồ treo tường. Miền
auth phát múi giờ người dùng lên `lifeos.user.events`, và habit, planning,
notification mỗi miền giữ một projection `user_settings` nhỏ — nhờ đó lời nhắc
07:00 nghĩa là 07:00 ở nơi người dùng đang ở.

**Yêu cầu/phản hồi qua bus.** Tóm tắt hằng ngày cần *thời điểm* (miền notification
nắm) và *nội dung* (miền planning nắm). Miền notification phát một `SummaryRequest`
và miền planning trả lời bằng một lời nhắc. Không miền nào đọc bảng của miền kia.

**Mỗi handler một transaction.** Handler sự kiện chạy `REQUIRES_NEW`. Một
projection hỏng không được phép làm rollback thao tác ghi đã sinh ra sự kiện — đó
đúng là điều mà broker trước đây bảo đảm chỉ nhờ việc nó nằm ở nơi khác.

---

## Bảo mật

| Biện pháp | Ở đâu |
|---|---|
| Băm mật khẩu bcrypt, cost 12 | `SecurityConfig` |
| Chính sách mật khẩu: từ 10 ký tự, có chữ và số | kiểm tra ở DTO, cưỡng chế phía server |
| **Xoay refresh token kèm phát hiện tái sử dụng** — token dùng lại sẽ thu hồi cả họ | `TokenService` |
| Chỉ lưu băm SHA-256 của refresh token | `auth.refresh_tokens.token_hash` |
| Xác thực hai lớp TOTP (RFC 6238) + mã khôi phục dùng một lần | `TotpService` |
| Google OAuth2 kèm PKCE, state dùng một lần | `GoogleOAuthService` |
| Khoá chống dò mật khẩu theo **cả tài khoản lẫn IP** | `LoginThrottleService` |
| Đăng nhập thời gian hằng định với tài khoản không tồn tại (bcrypt mồi thật) | `AuthService` |
| Bề mặt admin chặn bằng `@PreAuthorize` ở cấp class | `AdminController` |
| Nhật ký kiểm toán chỉ ghi thêm, transaction riêng nên sống sót qua rollback | `AuditService` |
| Header bảo mật + CSP chặt, **đứng trước** chuỗi filter của Spring Security | `SecurityHeadersFilter` |
| Đổi mật khẩu thu hồi mọi phiên khác | `AuthService.changePassword` |
| Profile `prod` từ chối khởi động với khoá ký mặc định hoặc mật khẩu admin mặc định | `StartupChecks` |

> `SecurityHeadersFilter` cố tình đứng ở vị trí `DEFAULT_FILTER_ORDER - 10`. Chuỗi
> filter của Spring Security tự trả lời request chưa xác thực mà **không** gọi
> xuống dưới, nên một filter xếp sau nó sẽ không bao giờ chạy trên response 401 —
> đúng loại response mà kẻ đang dò API nhìn thấy nhiều nhất.

> Hệ thống tạo sẵn tài khoản **admin / admin** cho lần đăng nhập đầu. Đổi mật
> khẩu rồi đặt `ADMIN_SEED_ENABLED=false`. Dưới profile `prod`, ứng dụng **từ
> chối khởi động** nếu mật khẩu vẫn là `admin`.

---

## Chạy trên máy của bạn

Cần: **JDK 21**, **Node 22**, và một Postgres 14+ (docker-compose có sẵn một cái).

```bash
# 1. Postgres — cổng 55432, không đụng Postgres sẵn có ở 5432
docker compose up -d

# 2. Backend — http://localhost:9080
cd backend
mvn spring-boot:run

# 3. Web app — http://localhost:5273
cd frontend
npm install
npm run dev
```

Mở <http://localhost:5273>, đăng nhập `admin` / `admin`.
Admin console ở <http://localhost:5273/admin/>.
API docs ở <http://localhost:9080/swagger-ui.html> (sáu nhóm, một nhóm mỗi miền).

**Không có Docker?** Bất kỳ Postgres nào cũng được:

```bash
createdb lifeos
export DATABASE_URL=jdbc:postgresql://localhost:5432/lifeos
export DATABASE_USERNAME=... DATABASE_PASSWORD=...
cd backend && mvn spring-boot:run
```

Ứng dụng tự tạo sáu schema và chạy migration khi khởi động. Không có bước setup
database thủ công nào.

### Trên Windows: scripts

Docker thường nằm trong WSL còn ứng dụng chạy thẳng trên Windows, nên `scripts/`
gói sẵn cả hai phía:

```powershell
# Postgres — project riêng `lifeos-mono` và container riêng, để chạy song song
# được với bản microservice (bản đó cũng khai báo container tên lifeos-postgres)
wsl -d Ubuntu -u root bash -lc 'cd /mnt/c/Users/nuan/dev/lifeos-mono && bash scripts/infra-up.sh'

# Backend + web app, log vào logs\, tự chờ tới khi /actuator/health báo UP.
# Thêm -Build nếu chưa có jar, -SkipFrontend nếu chỉ cần API.
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1

# Dừng cả backend lẫn web dev server; thêm -Infra để tắt luôn Postgres
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
```

`run-local.ps1` hạ chu kỳ scheduler xuống 15 giây (mặc định 5 phút) để một lời nhắc
quan sát được trong vài giây thay vì vài phút — chỉ khác đúng chỗ đó so với
`application.yml`.

---

## Kiểm chứng

```bash
cd backend && mvn verify
```

24 test, khoảng 30 giây. Bộ test **tự khởi động một Postgres thật**
(`io.zonky.test:embedded-postgres`) — không cần Docker, không cần database sẵn có,
chạy giống hệt trên máy bạn và trên GitHub Actions.

Lý do không dùng database in-memory: migration dùng `jsonb`, GIN index, partial
index, `md5()` trong unique index, và sáu schema. Một database "chế độ tương thích
Postgres" sẽ pass trên một schema mà ứng dụng thật không bao giờ chạy.

| Test | Chứng minh điều gì |
|---|---|
| `SchemaMigrationTest` | Sáu schema migrate vào một database; `ddl-auto: validate` khớp **mọi** entity với schema nó khai báo; ba bảng `user_settings` cùng tồn tại |
| `EndToEndFlowTest` | Check-in → event store → outbox → bus → rollup analytics → API. Và một lời nhắc lặp chỉ tạo một thông báo |
| `EveryContextRespondsTest` | Cả sáu miền trả lời qua HTTP; request không token bị 401; `/api/admin` bị 403 với tài khoản thường |
| `RunningServerTest` | Qua socket thật: health, header bảo mật trên response 401, preflight CORS được chấp nhận từ origin đúng và bị từ chối từ origin lạ |
| `StartupChecksTest` | Profile `prod` từ chối khoá ký mặc định, khoá ngắn, và mật khẩu admin mặc định |

CI (`.github/workflows/ci.yml`) chạy cả ba việc song song: test backend,
typecheck + build frontend, và **build luôn image Docker mà Render sẽ dựng** — để
một Dockerfile hỏng bị phát hiện trong CI chứ không phải giữa lúc deploy.

Ngoài ra, với ứng dụng đang chạy thật:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

68 khẳng định đi xuyên HTTP API: đăng ký, xoay token và phát hiện tái sử dụng,
check-in thói quen và XP, dựng lại projection từ event store, số học số dư sổ cái,
nhịp tiêu ngân sách, vòng đời công việc, dự án (tiến độ đếm từ task của nó, bộ lọc
task theo dự án, và việc từ chối xoá khi còn việc chưa xong), phiên tập trung,
rollup analytics, kiểm soát truy cập admin, chặn dò mật khẩu — và toàn bộ luồng nhắc
nhở, gồm việc scheduler thật sinh cảnh báo trước hạn và quét lặp lại **không** tạo
bản trùng. Thêm `-SkipSecurityChecks` để bỏ phần dò mật khẩu, vì nó tiêu hạn mức
đăng nhập sai theo IP (20 lần mỗi 15 phút).

```bash
cd frontend && node scripts/visual-check.mjs
```

Seed một tài khoản demo, lái Chromium qua mọi màn hình ở cả kích thước desktop lẫn
di động cộng admin console, ghi 30 ảnh vào `frontend/screenshots/` và **fail nếu có
bất kỳ lỗi console nào** — một bản build compile được vẫn có thể render ra trang
trắng.

---

## Cổng dịch vụ

Toàn bộ cổng đã được đổi so với bản microservice, nên hai bản chạy song song được
trên cùng một máy trong lúc bạn chuyển đổi.

| | Bản mới | Bản microservice cũ |
|---|---|---|
| Backend | **9080** | 8080–8086, 8761, 8000 |
| Web app (dev) | **5273** | 5173 |
| Admin (dev) | **5274** | 5174 |
| Vite preview | **4273** | 4173 |
| Postgres (local) | **55432** | 5432 / 15432 |

Trên Render, `PORT` được nền tảng tiêm vào và ứng dụng bind theo; 9080 chỉ là mặc
định khi chạy tại máy.

---

## Bố cục thư mục

```
lifeos-mono/
├── backend/
│   ├── Dockerfile                  image Render dựng
│   ├── pom.xml                     một module
│   └── src/main/
│       ├── java/com/lifeos/
│       │   ├── LifeOsApplication.java
│       │   ├── platform/           phần thay thế hạ tầng cũ
│       │   │   ├── bus/            InProcessEventPublisher, ReminderBus  ← Kafka, RabbitMQ
│       │   │   ├── store/          EphemeralStore                        ← Redis
│       │   │   └── config/         Flyway 6 schema, OpenAPI, header, StartupChecks
│       │   ├── common/             lớp dùng chung: JWT, lỗi, sự kiện
│       │   ├── auth/               tài khoản, token, 2FA, OAuth, admin
│       │   ├── habit/              event store + projection + gamification
│       │   ├── expense/            sổ cái, ngân sách, quy tắc lặp
│       │   ├── planning/           task, dự án, mục tiêu, focus, nhật ký
│       │   ├── analytics/          projector + read model liên miền
│       │   └── notification/       hộp thư, tuỳ chọn, web push, SSE
│       └── resources/
│           ├── application.yml
│           └── db/migration/       một thư mục mỗi schema
│               ├── auth/  habit/  expense/
│               └── planning/  notification/  analytics/
├── frontend/
│   ├── vercel.json                 rewrite SPA + header
│   ├── index.html   admin/index.html
│   └── src/                        app người dùng + admin console
├── .github/workflows/
│   ├── ci.yml                      test, build, build image
│   └── keep-alive.yml              ping Render mỗi 10 phút
├── docker-compose.yml              chỉ Postgres, cho dev
├── render.yaml                     blueprint Render
├── .env.example
└── DEPLOYMENT.md                   hướng dẫn từng bước
```

---

## Giới hạn đã biết

Nói thẳng, vì chúng là hệ quả trực tiếp của việc chọn gói miễn phí và một tiến
trình duy nhất:

- **Không scale ngang.** `LoginThrottleService` và `EphemeralStore` giữ trạng thái
  trong bộ nhớ. Chạy hai instance thì mỗi instance cho phép trọn hạn mức đăng nhập
  sai, và một challenge 2FA tạo ở instance này sẽ không tồn tại ở instance kia. Nếu
  có ngày cần nhiều instance, hai thứ này phải chuyển xuống bảng — không phải một
  broker.
- **Khởi động lại làm mất trạng thái tạm.** Đăng nhập 2FA đang dở và luồng OAuth
  đang dở sẽ hỏng khi deploy. Người dùng bấm lại. Không mất dữ liệu.
- **Cold start ~50 giây** trên gói free của Render sau 15 phút không có traffic.
  Workflow giữ ấm giảm bớt chứ không xoá bỏ.
- **Handler sự kiện chạy đồng bộ** trên thread của người gọi. Với lượng dữ liệu
  hiện tại thì không đáng kể, nhưng nó có nghĩa là một projection chậm sẽ làm chậm
  request đã sinh ra nó.
- **Rollup analytics của task tính theo ngày UTC**, vì một task không mang ngày
  riêng như giao dịch hay check-in. Ở phía đông Greenwich, bảy giờ đầu sau nửa đêm
  sẽ được tính vào ngày hôm trước.
- **`pg_trgm` là tuỳ chọn.** Nếu Postgres không cho cài, tìm kiếm theo chuỗi con
  trong ghi chú giao dịch vẫn đúng, chỉ là quét tuần tự. Migration tự bỏ qua index
  và ghi log.
- **Neon free ngủ sau 5 phút** không hoạt động; request đầu tiên chậm thêm khoảng
  nửa giây. Pool được cấu hình `max-lifetime` ngắn hơn thời gian Neon đóng kết nối,
  nên bạn không thấy lỗi "connection reset".

---

<details>
<summary><b>English summary</b></summary>

LifeOS is a habit / expense / planning app with cross-domain analytics. This is the
single-service edition: one Spring Boot process, one Postgres database with six
schemas, and a Vite SPA — designed to run entirely on free tiers (Neon + Render +
Vercel + GitHub Actions).

It was converted from a nine-module microservice system. Kafka became Spring's
`ApplicationEventPublisher`, RabbitMQ became a `ReminderBus`, MongoDB's analytics
read models became Postgres tables, Redis became an in-memory `EphemeralStore`, and
Eureka, Spring Cloud Gateway and nginx were removed entirely. Every feature, every
API endpoint and every screen survived; the transactional outbox and the habit
domain's event sourcing survived too, because they earn their place inside one
process as well.

- Deployment, step by step: [DEPLOYMENT.md](DEPLOYMENT.md)
- Run locally: `docker compose up -d` then `cd backend && mvn spring-boot:run` and
  `cd frontend && npm run dev`
- Test: `cd backend && mvn verify` — 24 tests against a real Postgres that the
  suite starts itself, no Docker required

Ports were all changed from the microservice edition (backend `9080`, web `5273`,
Postgres `55432`) so both can run side by side during a migration.

</details>
