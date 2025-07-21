🔧 Nếu gặp lỗi Chrome driver:

Cài Chrome trên server:

# Ubuntu/Debian

sudo apt update
sudo apt install google-chrome-stable

# CentOS/RHEL

sudo yum install google-chrome-stable

Hoặc dùng Firefox (thay ChromeDriver):

javaWebDriverManager.firefoxdriver().setup();
FirefoxOptions options = new FirefoxOptions();
options.addArguments("--headless");
driver = new FirefoxDriver(options);

Debug mode (tắt headless để xem browser):

java// Tạm thời bỏ dòng này để xem browser hoạt động
// options.addArguments("--headless");
