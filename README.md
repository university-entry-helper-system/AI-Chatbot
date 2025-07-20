# Education Chatbot API

Hệ thống chatbot tư vấn tuyển sinh đại học với API RESTful và giao diện Swagger.

## 🚀 Tính năng

- **Quản lý Đại học**: CRUD operations cho thông tin trường đại học
- **Quản lý Ngành học**: CRUD operations cho thông tin ngành học
- **API Documentation**: Giao diện Swagger UI tự động
- **Database**: MySQL với JPA/Hibernate
- **RESTful API**: Tuân thủ chuẩn REST

## 🛠️ Công nghệ sử dụng

- **Spring Boot 3.2.7**
- **Spring Data JPA**
- **MySQL Database**
- **Swagger/OpenAPI 3**
- **Maven**

## 📋 Yêu cầu hệ thống

- Java 17+
- MySQL 8.0+
- Maven 3.6+

## 🚀 Cài đặt và chạy

### 1. Clone repository

```bash
git clone <repository-url>
cd education-chatbot
```

### 2. Cấu hình database

Cập nhật thông tin database trong `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/education_chatbot
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 3. Build và chạy

```bash
mvn clean install
mvn spring-boot:run
```

### 4. Truy cập ứng dụng

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Documentation**: http://localhost:8080/api-docs

## 📚 API Endpoints

### Đại học (Universities)

- `GET /api/universities` - Lấy danh sách tất cả đại học
- `GET /api/universities/{id}` - Lấy thông tin đại học theo ID
- `POST /api/universities` - Tạo đại học mới
- `PUT /api/universities/{id}` - Cập nhật thông tin đại học
- `DELETE /api/universities/{id}` - Xóa đại học
- `GET /api/universities/search?keyword={keyword}` - Tìm kiếm đại học

### Ngành học (Programs)

- `GET /api/programs` - Lấy danh sách tất cả ngành học
- `GET /api/programs/{id}` - Lấy thông tin ngành học theo ID
- `GET /api/programs/university/{universityId}` - Lấy ngành học theo đại học
- `POST /api/programs` - Tạo ngành học mới
- `GET /api/programs/search?keyword={keyword}` - Tìm kiếm ngành học

## 🔧 Cấu hình Swagger

Swagger UI được cấu hình với các tùy chọn sau:

- **Path**: `/swagger-ui.html`
- **API Docs**: `/api-docs`
- **Operations Sorter**: Theo method
- **Tags Sorter**: Theo alphabet
- **Doc Expansion**: None (gọn gàng)
- **Request Duration**: Hiển thị thời gian request

## 📖 Sử dụng Swagger UI

1. **Truy cập**: Mở trình duyệt và vào http://localhost:8080/swagger-ui.html
2. **Xem API**: Tất cả endpoints được hiển thị với documentation chi tiết
3. **Test API**: Click vào endpoint và sử dụng "Try it out" để test
4. **Schema**: Xem cấu trúc request/response trong phần "Schemas"

## 🗄️ Database Schema

### University

- `id` (Long, Primary Key)
- `name` (String, Not Null)
- `code` (String, Unique)
- `fullName` (Text)
- `location` (String)
- `type` (String)
- `website` (String)
- `description` (Text)
- `totalQuota` (Integer)

### Program

- `id` (Long, Primary Key)
- `name` (String, Not Null)
- `code` (String)
- `description` (Text)
- `universityId` (Long, Foreign Key)
- `benchmarkScore2022` (Float)
- `benchmarkScore2023` (Float)
- `benchmarkScore2024` (Float)
- `quota` (Integer)
- `admissionMethod` (String)
- `subjectCombination` (String)
- `tuitionFee` (String)
- `careerProspects` (Text)
- `note` (Text)

## 👨‍💻 Tác giả

**KhoiPD8**

- Email: khoipd8@gmail.com
- GitHub: https://github.com/khoipd8

## 📄 License

MIT License - xem file [LICENSE](LICENSE) để biết thêm chi tiết.
