# Orchestragent: ghi chú kiến trúc cho báo cáo

Tài liệu này giải thích **vì sao** hệ thống có hình dạng hiện tại, **làm thế nào** để tách nó thành các dịch vụ
riêng khi cần, và **những gì đã chủ động bỏ qua**. Mô tả chức năng và cách chạy nằm trong [README.md](../README.md),
quy tắc cho người và AI nằm trong [AGENTS.md](../AGENTS.md).

## 1. Bài toán

Orchestragent là ứng dụng web để người và AI agent cùng làm việc trên các issue. Người giao issue cho agent; một
**daemon** chạy trên máy của người đó nhận việc, chạy Claude Code CLI, gửi tiến trình về theo thời gian thực, rồi
chuyển issue sang trạng thái chờ duyệt. Người duyệt (Approve) hoặc gửi lại kèm nhận xét (Request changes). Agent
không bao giờ tự đặt issue sang "done".

Ba đặc điểm quyết định kiến trúc:

- Có **hai loại client rất khác nhau**: trình duyệt (cookie, CSRF, SSE) và daemon (API token, polling, retry khi mất
  mạng). Chúng có hai chuỗi bảo mật riêng trong cùng một ứng dụng.
- Công việc của agent **chạy ngoài server**, trên máy người dùng, nên server chỉ điều phối: hàng đợi, lease, log,
  kết quả.
- Nhiều thao tác là **phản ứng theo sự kiện** (gán issue → tạo task; task xong → comment + đổi trạng thái +
  thông báo), cần chạy đúng một lần dù sự kiện có thể đến nhiều lần.

## 2. Vì sao chọn modular monolith

| Lựa chọn | Điểm mạnh | Điểm yếu trong bối cảnh này |
| --- | --- | --- |
| Monolith không phân lớp | Nhanh nhất lúc đầu | Các phần dính vào nhau, về sau không thể tách |
| Microservice ngay từ đầu | Tách sẵn, scale riêng | Chi phí vận hành lớn (nhiều dịch vụ, broker, tracing, nhiều database) cho một nhóm nhỏ; giao dịch giữa các phần phải thành saga; khó chạy và khó test trên một máy |
| **Modular monolith** (đã chọn) | Một ứng dụng để chạy, test, triển khai; giao dịch cục bộ; ranh giới vẫn rõ như dịch vụ | Cần kỷ luật để ranh giới không bị phá, nên ranh giới được **kiểm bằng máy** |

Lý do cụ thể:

- **Một nhóm nhỏ, một người triển khai.** Một tiến trình, một database, một lệnh `docker compose up -d` là đủ để
  chạy toàn bộ hệ thống. Test tích hợp dùng Testcontainers với một Postgres.
- **Giao dịch cục bộ khi cần.** Ví dụ gán issue và ghi sự kiện cùng một giao dịch (transactional outbox); không cần
  saga cho những thao tác mà việc mất đồng bộ không chấp nhận được.
- **Ranh giới đã sẵn sàng để tách.** Mỗi bounded context (`identity`, `workspace`, `issue`, `agent`, `inbox`) chia
  thành bốn module Gradle theo Clean Architecture (`domain`, `application`, `infrastructure`, `contract`). Phần
  `contract` là API công khai của context; không context nào được import phần bên trong của context khác.
- **Quyết định tách được hoãn đến lúc có số liệu** (tải, nhóm phát triển lớn hơn), thay vì đoán trước.

## 3. Những quy tắc giữ đường tách mở (và cách kiểm)

| Quy tắc | Cách kiểm |
| --- | --- |
| Context chỉ phụ thuộc `*-contract` của context khác | Phụ thuộc Gradle (module khác không nằm trong classpath) và ArchUnit trong `backend/app` |
| `domain` là Java thuần, không Spring/JPA/Jackson | ArchUnit |
| `application` không biết web, JPA, infrastructure | ArchUnit |
| `platform` không biết context nào | ArchUnit |
| Mỗi context có **schema Postgres riêng** và thư mục Flyway riêng, không JOIN và không khoá ngoại giữa các schema | `SchemaIsolationTest` |
| Giao tiếp bất đồng bộ qua **sự kiện tích hợp** (Spring Modulith, outbox), listener **idempotent** | Test idempotent trên fake và trên Postgres thật (giao một sự kiện hai lần) |
| ID là UUIDv7 sinh ở tầng ứng dụng | Không cần sequence của database, nên ID không phụ thuộc database nào |
| Tham chiếu giữa context chỉ là UUID thường | Không có khoá ngoại xuyên schema |

Các quy tắc ArchUnit có test riêng chứng minh chúng **có hiệu lực** (`ArchitectureRulesAreEffectiveTest`): một lớp
vi phạm cố ý phải làm quy tắc thất bại.

## 4. Cách tách thành microservice

Ba thay đổi, làm từng context một. Mỗi bước giữ nguyên code nghiệp vụ.

### 4.1. Contract trở thành HTTP client

Mọi lời gọi giữa các context đi qua một interface trong `*-contract` (`WorkspaceAccess`, `WorkspaceDirectory`,
`UserDirectory`, `AgentDirectory`, `IssueDirectory`, `ApiTokenVerifier`). Khi tách một context ra:

- Context bị tách **giữ nguyên** implementation của facade và phơi nó ra thành REST nội bộ.
- Các context còn lại thay bean facade bằng một **HTTP client** cùng interface (cùng DTO trong `*-contract`). Code
  nghiệp vụ không đổi vì nó chỉ biết interface.
- Cần thêm: timeout, retry có giới hạn, circuit breaker, và cache ngắn cho những lời gọi nóng nhất. Điển hình là
  `WorkspaceAccess.requireMember` (gọi ở gần như mọi request), nên cache theo `(workspaceId, userId)` vài giây.
- Lỗi: `NotFoundException`/`ForbiddenException` hiện là exception cục bộ; HTTP client phải dịch mã 404/403 trở lại
  đúng các exception này.

### 4.2. Sự kiện trở thành Kafka (hoặc broker khác)

Sự kiện tích hợp (`IssueAssigneeChanged`, `CommentAdded`, `AgentTaskCompleted`...) hiện được Spring Modulith ghi
vào bảng `event_publication` trong cùng giao dịch rồi giao cho listener sau khi commit.

- Đánh dấu các record sự kiện để Spring Modulith **externalize** chúng ra broker (Kafka); outbox vẫn là nơi bảo đảm
  "ghi nghiệp vụ và ghi sự kiện cùng giao dịch".
- Listener của context khác trở thành consumer của topic. **Phần khó nhất đã làm sẵn**: mọi listener đã idempotent
  theo `source_event_id` (khoá duy nhất), nên giao ít nhất một lần của Kafka là chấp nhận được.
- Thứ tự: sự kiện của cùng một issue nên chung một partition key (`issueId`) để giữ thứ tự tương đối.

### 4.3. Schema trở thành database riêng

Vì không có JOIN hay khoá ngoại xuyên schema, mỗi schema có thể chuyển sang một database (hoặc một instance) bằng
cách đổi `DB_URL` của dịch vụ đó. Flyway đã chạy theo từng module với bảng lịch sử riêng nên migration đi theo
context. Dữ liệu cần ở nơi khác đã được **sao chép có chủ đích** thay vì JOIN (ví dụ thông báo trong inbox giữ bản sao
mã issue như `WEB-12` và tiêu đề issue, tên người và agent). Sao chép mã issue an toàn vì key của project không bao
giờ đổi (bước 13).

### 4.4. Những phần dùng chung phải đổi

| Phần hiện tại | Vấn đề khi nhiều instance | Hướng xử lý |
| --- | --- | --- |
| `SseHub` giữ kết nối trong bộ nhớ | Sự kiện chỉ đến trình duyệt nối vào đúng instance đó | Pub/sub dùng chung (Redis hoặc Kafka) rồi mỗi instance đẩy cho kết nối của nó |
| `SlidingWindowRateLimiter` trong bộ nhớ | Mỗi instance đếm riêng (giới hạn lỏng hơn, không chặt hơn) | Đặt ở API gateway, hoặc Redis |
| Cookie phiên là JWT ký bằng `JWT_SECRET` | Mọi dịch vụ cần xác thực được | Gateway xác thực và chuyển tiếp danh tính, hoặc chia sẻ khoá công khai (đổi sang RS256) |
| Job định kỳ (hết lease, runtime offline) | Nhiều instance cùng chạy | Khoá phân tán (ví dụ ShedLock) hoặc chỉ một instance chạy job. Câu lệnh claim dùng `FOR UPDATE SKIP LOCKED` nên đã an toàn khi nhiều instance cùng lấy task |

### 4.5. Thứ tự tách đề xuất

1. **`inbox`**: chỉ nhận sự kiện và trả danh sách; ít phụ thuộc nhất, rủi ro thấp, là bài tập tốt cho Kafka và HTTP client.
2. **`agent`**: có mẫu tải riêng (daemon polling mỗi vài giây, log dồn dập), nên được lợi nhất khi scale riêng.
3. **`issue`** và **`workspace`**.
4. **`identity`** cuối cùng, vì mọi thứ khác phụ thuộc vào nó.

## 5. Các quyết định thiết kế đáng nhắc

- **Cổng duyệt (review gate)**: agent chỉ chuyển issue sang "in review"; chỉ người mới chuyển sang "done". Gửi lại
  kèm nhận xét tạo đúng một task mới, chạy tiếp cùng phiên và cùng thư mục.
- **Idempotent theo sự kiện nguồn**: task và comment do một sự kiện sinh ra lưu `source_event_id` (duy nhất), nên
  giao lại sự kiện không sinh thêm gì. Thay cho khoá `(issue, trigger, comment)` ban đầu trong kế hoạch.
- **Claim task bằng một câu SQL** với `FOR UPDATE SKIP LOCKED` và lease 60 giây: hai daemon không bao giờ nhận cùng
  một task, và một issue không chạy hai lần cùng lúc.
- **Project nằm trong context `issue`** (bước 13), không thành context riêng: số của issue mới được cấp theo project
  trong cùng giao dịch tạo issue, bằng một câu `UPDATE ... RETURNING` vốn đã từ chối project đã lưu trữ. Key do người
  dùng đặt, duy nhất trong workspace (kể cả project đã lưu trữ) và **không đổi**, nên mã issue `KEY-n` được phép chép
  sang inbox, prompt của agent và tên thư mục làm việc. Issue giữ bản sao key, được một khoá ngoại ghép
  `(workspace_id, project_id, project_key)` giữ cho đúng. Dữ liệu cũ được migration chuyển vào project "General" (key
  `GEN`) của từng workspace, giữ nguyên số.
- **Daemon API tách khỏi API trình duyệt**: chuỗi bảo mật riêng, chỉ nhận API token (`oat_...`), không cookie, không
  CSRF. Mỗi lời gọi kiểm token thuộc về chủ của runtime hoặc task.
- **Người ngoài workspace nhận 404, không phải 403**, để không lộ sự tồn tại của workspace.
- **Giao diện nhận sự kiện "chỉ nói cái gì đã đổi"** rồi tải lại dữ liệu, nên sự kiện lặp hoặc đến sai thứ tự không
  thể hiện dữ liệu cũ hơn server.
- **Frontend parse mọi response bằng zod**, không dùng `as` hoặc `any` ngoài `shared/ui`.

## 6. Kiểm thử

- Backend: JUnit, AssertJ, MockMvc, Testcontainers (Postgres thật), ArchUnit. Test tích hợp chạy qua đúng chuỗi bảo
  mật thật, không tắt CSRF hay xác thực.
- Daemon: server giả bằng `HttpServer` của JDK mô phỏng quy tắc của server thật; provider Claude được test bằng một
  chương trình `claude` giả chạy như tiến trình riêng, cộng một bản ghi thật của Claude Code 2.1.311 làm fixture.
  **Test không bao giờ gọi `claude` thật**; smoke test thật chỉ chạy khi đặt `ORCHESTRAGENT_RUN_REAL_CLAUDE=1`.
- Frontend: Vitest + Testing Library với một server giả từ chối mọi request chưa có handler.
- End-to-end: Playwright, một câu chuyện đầy đủ (đăng nhập → workspace → daemon mock → agent → issue → duyệt hai
  lần → done → inbox) trên trình duyệt, backend, database và daemon thật.
- Kỹ thuật "phá quy tắc": sau khi viết test cho một quy tắc, tạm thời phá quy tắc đó và xác nhận đúng test thất bại.

## 7. Giới hạn đã chủ động bỏ qua

Những điều dưới đây là quyết định phạm vi, không phải sơ suất. Mỗi điều đều có thể làm tiếp.

**Vận hành và quy mô**

- Chạy **một instance**: SSE, giới hạn tốc độ đăng nhập và các job định kỳ giả định điều này (xem 4.4).
- Giới hạn tốc độ chỉ áp cho `POST /api/auth/code` (theo địa chỉ IP). Chưa có giới hạn cho các API khác và chưa có
  giới hạn số lời mời, workspace hay agent mỗi người.
- Danh sách issue trả tối đa 1000; chưa có phân trang đầy đủ. Thống kê dùng ngày UTC.
- Chưa có audit log, quan sát (metrics, tracing) và cấu hình triển khai (Docker image, CI/CD).

**Daemon và agent**

- Daemon đăng ký ở các workspace người dùng đang có **lúc khởi động**; tham gia workspace mới cần khởi động lại daemon.
- Huỷ task để issue ở trạng thái hiện tại (thường là "in progress") cho tới khi ai đó chuyển.
- `AgentTaskStarted` và `AgentTaskCompleted` chạy trên hai thread; về lý thuyết chúng có thể đổi chỗ nếu đến cách
  nhau vài mili giây và để issue ở "in progress". Chưa thấy xảy ra trong thực tế.
- Hai sự kiện đua nhau có thể làm một listener vi phạm khoá duy nhất; Modulith giao lại và lần sau bỏ qua.
- Chế độ `bypassPermissions` của Claude cho agent quyền của chính tài khoản người dùng. Daemon chỉ dùng nó khi người
  dùng tự đặt và có in cảnh báo; mặc định là `acceptEdits`.
- Chỉ có một bản ghi thật của Claude Code (haiku, 2.1.311); các loại message mới hơn bị bỏ qua có chủ ý.

**Giao diện và dữ liệu**

- Chưa có điều hướng cho màn hình hẹp (thanh bên bị ẩn dưới `md`).
- Chưa gỡ gán issue của thành viên rời workspace.
- Không chuyển được issue sang project khác và không đổi được key của project (mã issue phải giữ nguyên); chưa có
  quyền hay thành viên riêng theo project. Workspace mới chưa có project nào cho tới khi người dùng tạo.
- Dữ liệu seed của profile `dev` không có agent (agent cần runtime, mà runtime chỉ có khi daemon thật đăng ký).
- E2E có một kịch bản và chỉ chạy trên Chrome (hoặc Edge); chưa chạy trong CI.

**Bảo mật cần người xem xét**

- Đăng nhập bằng mã email, API token và daemon API (`DaemonSecurityConfig`, `DaemonController`,
  `TaskExecutionService`, câu lệnh claim trong `TaskRepositoryAdapter`) cần được người xem lại trước khi đưa vào dùng
  thật. Mã đăng nhập và token không bao giờ được ghi log.
