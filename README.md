# Tour Moment

Tour Moment (JourneyLog) gồm ứng dụng Android viết bằng Java, REST API Node.js/Express và MongoDB. Nhánh phát triển chính là `dev`.

## Yêu cầu

- Android Studio, Android SDK 36 và JDK 21
- Node.js 22 trở lên
- Không cần cài MongoDB nếu dùng `npm run start:local`

## Chạy backend và database local

```powershell
cd tour-backend
npm install
npm run start:local
```

Lệnh trên chạy một MongoDB dành riêng cho môi trường phát triển, lưu dữ liệu trong `tour-backend/.data/`, khởi động API tại `http://localhost:3000` và tạo tour mẫu nếu database đang trống. Các thư mục runtime này không được commit.

Kiểm tra trạng thái:

```powershell
Invoke-RestMethod http://localhost:3000/health
Invoke-RestMethod http://localhost:3000/api/tours
```

Có thể dùng `docker compose up --build` trong `tour-backend`. Cấu hình Docker mặc định dùng MongoDB local; đặt `MONGODB_URI` và `BASE_URL` trong môi trường nếu muốn kết nối dịch vụ khác.

## Chạy Android

1. Mở thư mục `Mycurrenttour` bằng Android Studio và chờ Gradle Sync.
2. Tạo `Mycurrenttour/local.properties` (file này không được commit):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
API_BASE_URL=http\://10.0.2.2\:3000/
MAPBOX_ACCESS_TOKEN=
```

`10.0.2.2` là địa chỉ máy host nhìn từ Android Emulator. Với điện thoại thật, thay bằng IP LAN của máy chạy backend. URL phải kết thúc bằng `/`.

3. Đặt file Firebase của môi trường phát triển tại `Mycurrenttour/app/google-services.json`. File chứa định danh dự án nên được cấp riêng và không commit lên Git.
4. Run cấu hình `app`. Có thể chọn **Continue as Guest** nếu không cần thử đăng nhập Firebase.

Nếu không khai báo `API_BASE_URL`, bản build dùng API Cloud Run hiện tại. HTTP cleartext chỉ được bật trong debug build để gọi backend local; release build vẫn chặn cleartext.

## Biến môi trường backend

Sao chép `tour-backend/.env.example` thành `tour-backend/.env` khi chạy với MongoDB riêng. File `.env` ở thư mục gốc không được backend đọc.

- `MONGODB_URI`: chuỗi kết nối MongoDB.
- `BASE_URL`: public URL dùng để tạo đường dẫn ảnh upload.
- `DEEPSEEK_API_KEY`: tùy chọn, chỉ cần cho chatbot.
- `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL`: tùy chọn cấu hình chatbot.

## Kiểm thử

```powershell
cd tour-backend
npm test

cd ../Mycurrenttour
./gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

APK debug được tạo tại `Mycurrenttour/app/build/outputs/apk/debug/app-debug.apk`.

## Quy tắc repository

Không commit `.env`, `google-services.json`, `node_modules`, file upload, database local, output build hoặc cấu hình IDE theo máy. Chỉ commit source code, lockfile, test và cấu hình mẫu không chứa secret.
