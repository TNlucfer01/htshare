# 📁 Desktop File Share

A minimalist JavaFX desktop application for sharing files over your local network with QR code support. Features a beautiful UI with dark/light themes and instant mobile connectivity.

![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)
![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

## ✨ Features

- 🎨 **Minimalist Design** - Clean, modern interface
- 🌓 **Dark/Light Theme** - Toggle between themes instantly
- 📱 **QR Code Connection** - Scan to connect from mobile
- 🚀 **Fast File Sharing** - Direct HTTP file server
- 📂 **Folder Navigation** - Browse directories easily
- 🔒 **Local Network** - Secure, no internet required
- 💻 **Cross-Platform** - Works on Windows, macOS, Linux

## 🖼️ Screenshots

### Light Theme
```
┌─────────────────────────────────────┐
│  📁 File Share            🌙        │
│  Share files securely over network  │
├─────────────────────────────────────┤
│                                     │
│  Select Folder                      │
│  [Browse...] /path/to/folder        │
│                                     │
│  Server Control                     │
│  [Start Server] [Stop Server]       │
│                                     │
│  Scan QR Code to Connect            │
│  ┌─────────────────────┐           │
│  │  ▪▪▪▪▪▪▪  ▪▪▪▪▪▪▪  │           │
│  │  ▪     ▪  ▪     ▪  │           │
│  │  ▪ ▪▪▪ ▪  ▪ ▪▪▪ ▪  │           │
│  │  ▪▪▪▪▪▪▪  ▪▪▪▪▪▪▪  │           │
│  └─────────────────────┘           │
│  http://192.168.1.100:8080         │
│                                     │
│  ✓ Server running                  │
└─────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Installation

1. **Clone or download the project**
```bash
git clone <your-repo-url>
cd desktop-file-share
```

2. **Build the project**
```bash
mvn clean package
```

3. **Run the application**
```bash
mvn javafx:run
```

Or run the JAR directly:
```bash
java -jar target/desktop-file-share-1.0.0.jar
```

## 📖 Usage

1. **Launch the application**
2. **Click "Browse..."** to select a folder to share
3. **Click "Start Server"** to begin sharing
4. **Scan the QR code** with your mobile device's camera
5. **Browse and download files** through your mobile browser

### Manual Connection

If QR code doesn't work, manually enter the URL shown in the app:
```
http://YOUR_IP:8080
```

## 🔧 Configuration

### Change Server Port

Edit `MainController.java`:
```java
private static final int DEFAULT_PORT = 8080; // Your port here
```

### Customize Themes

Modify CSS files in `src/main/resources/css/`:
- `light-theme.css` - Light mode colors
- `dark-theme.css` - Dark mode colors

## 🏗️ Project Structure

```
desktop-file-share/
├── src/
│   ├── main/
│   │   ├── java/com/htshare/
│   │   │   ├── MainApp.java              # Entry point
│   │   │   ├── server/
│   │   │   │   └── FileServer.java        # HTTP server
│   │   │   ├── ui/
│   │   │   │   └── MainController.java    # UI logic
│   │   │   └── util/
│   │   │       ├── NetworkUtils.java      # Network helpers
│   │   │       └── QRCodeGenerator.java   # QR generation
│   │   └── resources/
│   │       ├── fxml/main.fxml             # UI layout
│   │       ├── css/
│   │       │   ├── light-theme.css        # Light theme
│   │       │   └── dark-theme.css         # Dark theme
│   │       └── logback.xml                # Logging config
│   └── test/
└── pom.xml                                 # Maven config
```

## 🛠️ Technologies

- **JavaFX** - Modern UI framework
- **NanoHTTPD** - Lightweight HTTP server
- **ZXing** - QR code generation
- **SLF4J + Logback** - Logging
- **Maven** - Build tool

## 🔒 Security

⚠️ **Important:** This application is designed for local network use only.

- No authentication by default
- HTTP (not HTTPS) protocol
- Anyone on your network can access shared files
- Do not expose to the internet

For production use, implement:
- Authentication (JWT tokens)
- HTTPS/TLS encryption
- Access control lists
- Rate limiting

## 🐛 Troubleshooting

### Server Won't Start

**Check if port is in use:**
```bash
# Windows
netstat -ano | findstr :8080

# Linux/Mac
lsof -i :8080
```

### Mobile Can't Connect

1. Ensure both devices are on the same Wi-Fi network
2. Check firewall settings (allow port 8080)
3. Try manual connection with IP address

### QR Code Not Showing

1. Verify server started successfully
2. Check console for errors
3. Ensure network connection is active

## 📊 Performance

- **Memory:** ~15MB footprint
- **CPU:** Minimal usage
- **Network:** Direct streaming, no buffering
- **Concurrent:** Handles multiple connections

## 🗺️ Roadmap

- [ ] File upload from mobile
- [ ] Multi-folder selection
- [ ] Transfer history
- [ ] Password protection
- [ ] HTTPS support
- [ ] Custom port in GUI
- [ ] Drag & drop folders
- [ ] System tray integration

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📄 License

This project is open source and available under the MIT License.

## 🙏 Acknowledgments

- [JavaFX](https://openjfx.io/) - UI framework
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - HTTP server
- [ZXing](https://github.com/zxing/zxing) - QR code library

## 📧 Contact

For questions or feedback, please open an issue on GitHub.

---

**Made with ❤️ using JavaFX**
