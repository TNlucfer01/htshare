package com.htshare.server;

import com.htshare.util.SSLCertificateGenerator;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTTPS File Server using NanoHTTPD with SSL/TLS support */
public class HttpsFileServer extends NanoHTTPD {
  private static final Logger logger = LoggerFactory.getLogger(HttpsFileServer.class);
  private static final int MIN_PORT = 8443; // Standard HTTPS port range
  private static final int MAX_PORT = 8543;

  private final File rootDirectory;
  private final int actualPort;
  private final ConnectionMonitor connectionMonitor;
  private final boolean useHttps;
  private SSLContext sslContext;

  /** Create HTTP file server (non-SSL) */
  public HttpsFileServer(int preferredPort, File rootDirectory) throws IOException {
    this(preferredPort, rootDirectory, 0, null, false);
  }

  /** Create file server with optional HTTPS and timeout */
  public HttpsFileServer(
      int preferredPort,
      File rootDirectory,
      int timeoutMinutes,
      Runnable onTimeout,
      boolean useHttps)
      throws IOException {
    super(findAvailablePort(preferredPort,  8080,  8180));

    if (!rootDirectory.exists() || !rootDirectory.isDirectory()) {
      throw new IOException("Invalid root directory: " + rootDirectory);
    }

    this.rootDirectory = rootDirectory;
    this.actualPort = getListeningPort();
    this.connectionMonitor = new ConnectionMonitor(timeoutMinutes, onTimeout);
    this.useHttps = useHttps;

    if (useHttps) {
      try {
        setupSSL();
        logger.info("HTTPS File Server initialized on port {} with SSL", actualPort);
      } catch (Exception e) {
        logger.error("Failed to setup SSL, falling back to HTTP", e);
        throw new IOException("SSL setup failed: " + e.getMessage());
      }
    } else {
      logger.info("HTTP File Server initialized on port {}", actualPort);
    }

    logger.info("Serving from: {}", rootDirectory.getAbsolutePath());
  }

  /** Find available port in range */
  private static int findAvailablePort(int preferred, int min, int max) throws IOException {
    preferred = (preferred == -1) ? min : preferred;
    
    // First try the preferred port
    try (ServerSocket socket = new ServerSocket(preferred)) {
      logger.info("Found available port: {}", preferred);
      return preferred;
    } catch (IOException e) {
      logger.debug("Preferred port {} not available", preferred);
    }
    
    // Try all ports in the range
    for (int port = min; port <= max; port++) {
      if (port == preferred) continue; // Skip preferred port as we already tried it
      try (ServerSocket socket = new ServerSocket(port)) {
        logger.info("Found available port: {}", port);
        return port;
      } catch (IOException e) {
        logger.debug("Port {} not available", port);
      }
    }

    throw new IOException("No ports available in range " + min + "-" + max);
  }

  /** Setup SSL/TLS with self-signed certificate */
  private void setupSSL() throws Exception {
    // Create self-signed certificate in-memory
    KeyStore keyStore = SSLCertificateGenerator.generateKeyStore();

    // Setup SSL context
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, SSLCertificateGenerator.KEYSTORE_PASSWORD.toCharArray());

    sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);

    // Make NanoHTTPD use SSL
    makeSecure(sslContext.getServerSocketFactory(), null);

    logger.info("SSL/TLS configured successfully with self-signed certificate");
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    String method = session.getMethod().toString();

    // Record activity
    connectionMonitor.recordRequest(uri, method);

    logger.info("{} {} - {}", method, uri, session.getRemoteIpAddress());

    try {
      // Decode URI
      uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);

      // Remove leading slash
      if (uri.startsWith("/")) {
        uri = uri.substring(1);
      }

      // Handle API endpoints
      if (uri.startsWith("api/")) {
        return handleApiRequest(uri, session);
      }

      // Handle root or directory listing
      if (uri.isEmpty()) {
        return serveDirectoryListing(rootDirectory, "");
      }

      // Resolve file path
      File file = new File(rootDirectory, uri);

      // Security: prevent directory traversal
      if (!file.getCanonicalPath().startsWith(rootDirectory.getCanonicalPath())) {
        logger.warn("Directory traversal attempt: {}", uri);
        return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied");
      }

      if (!file.exists()) {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found");
      }

      if (file.isDirectory()) {
        return serveDirectoryListing(file, uri);
      }

      // Serve file
      return serveFile(file);

    } catch (Exception e) {
      logger.error("Error serving request", e);
      return newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          MIME_PLAINTEXT,
          "Internal server error: " + e.getMessage());
    }
  }

  /** Handle API requests */
  private Response handleApiRequest(String uri, IHTTPSession session) {
    if (uri.equals("api/stats")) {
      return handleStatsApi();
    } else if (uri.startsWith("api/list")) {
      return handleListApi(session);
    }

    return newFixedLengthResponse(
        Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Endpoint not found\"}");
  }

  /** Stats API endpoint */
  private Response handleStatsApi() {
    ConnectionMonitor.ConnectionStats stats = connectionMonitor.getStats();

    String json =
        String.format(
            "{\"totalRequests\":%d,\"totalDownloads\":%d,\"activeConnections\":%d,"
                + "\"secondsSinceLastActivity\":%d,\"hasActivity\":%b}",
            stats.getTotalRequests(),
            stats.getTotalDownloads(),
            stats.getActiveConnections(),
            stats.getSecondsSinceLastActivity(),
            stats.hasHadActivity());

    Response response = newFixedLengthResponse(Response.Status.OK, "application/json", json);
    response.addHeader("Access-Control-Allow-Origin", "*");
    return response;
  }

  /** List API endpoint */
  private Response handleListApi(IHTTPSession session) {
    try {
      String path = session.getParms().getOrDefault("path", "");
      File directory = new File(rootDirectory, path);

      if (!directory.getCanonicalPath().startsWith(rootDirectory.getCanonicalPath())) {
        return newFixedLengthResponse(
            Response.Status.FORBIDDEN, "application/json", "{\"error\":\"Access denied\"}");
      }

      if (!directory.exists() || !directory.isDirectory()) {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Directory not found\"}");
      }

      String json = generateDirectoryJson(directory);
      Response response = newFixedLengthResponse(Response.Status.OK, "application/json", json);
      response.addHeader("Access-Control-Allow-Origin", "*");
      return response;

    } catch (Exception e) {
      logger.error("Error in list API", e);
      return newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  /** Serve a file */
  private Response serveFile(File file) throws IOException {
    String mimeType = getMimeType(file.getName());
    FileInputStream fis = new FileInputStream(file);

    Response response = newChunkedResponse(Response.Status.OK, mimeType, fis);
    response.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
    response.addHeader("Accept-Ranges", "bytes");
    response.addHeader("Content-Length", String.valueOf(file.length()));

    // Record download
    connectionMonitor.recordDownload(file.getName(), file.length());

    logger.info("Serving file: {} ({} bytes)", file.getName(), file.length());
    return response;
  }

  /** Serve directory listing */
  private Response serveDirectoryListing(File directory, String relativePath) {
    String html = generateDirectoryHtml(directory, relativePath);

    Response response = newFixedLengthResponse(Response.Status.OK, "text/html", html);
    response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    return response;
  }

  /** Generate HTML for directory listing */
  private String generateDirectoryHtml(File directory, String relativePath) {
    StringBuilder html = new StringBuilder();

    html.append("<!DOCTYPE html><html lang=\"en\"><head>");
    html.append("<meta charset=\"UTF-8\">");
    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
    html.append("<title>").append(escapeHtml(directory.getName())).append(" - File Share</title>");

    // Add lock icon for HTTPS
    String protocol = useHttps ? "🔒 HTTPS" : "HTTP";

    html.append("<style>");
    html.append(getEmbeddedCss());
    html.append("</style></head><body>");

    // Header with security badge
    html.append("<div class=\"container\">");
    html.append("<div class=\"header\">");
    html.append("<h1>📁 ").append(escapeHtml(directory.getName())).append("</h1>");
    html.append("<p class=\"subtitle\">Shared Folder</p>");
    html.append("<span class=\"security-badge\">").append(protocol).append("</span>");
    html.append("</div>");

    // Breadcrumb navigation
    if (!relativePath.isEmpty()) {
      html.append("<div class=\"breadcrumb\">");
      html.append("<a href=\"/\">🏠 Home</a>");

      String[] parts = relativePath.split("/");
      String currentPath = "";
      for (String part : parts) {
        if (!part.isEmpty()) {
          currentPath += "/" + part;
          html.append(" / <a href=\"")
              .append(currentPath)
              .append("\">")
              .append(escapeHtml(part))
              .append("</a>");
        }
      }
      html.append("</div>");
    }

    // File listing
    File[] files = directory.listFiles();

    if (files == null || files.length == 0) {
      html.append("<div class=\"empty\">📭 This folder is empty</div>");
    } else {
      List<File> dirs = new ArrayList<>();
      List<File> regularFiles = new ArrayList<>();

      for (File f : files) {
        if (f.isDirectory()) {
          dirs.add(f);
        } else {
          regularFiles.add(f);
        }
      }

      dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      regularFiles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

      html.append("<ul class=\"file-list\">");

      // Parent directory link
      if (!relativePath.isEmpty()) {
        String parentPath =
            relativePath.contains("/")
                ? relativePath.substring(0, relativePath.lastIndexOf("/"))
                : "";
        html.append("<li class=\"file-item\">");
        html.append("<a href=\"/").append(parentPath).append("\">");
        html.append("<span class=\"icon\">⬆️</span>");
        html.append("<span class=\"name\">..</span>");
        html.append("</a></li>");
      }

      // Directories
      for (File dir : dirs) {
        String path = relativePath.isEmpty() ? dir.getName() : relativePath + "/" + dir.getName();
        html.append("<li class=\"file-item\">");
        html.append("<a href=\"/").append(path).append("\">");
        html.append("<span class=\"icon\">📁</span>");
        html.append("<span class=\"name\">").append(escapeHtml(dir.getName())).append("</span>");
        html.append("</a></li>");
      }

      // Files
      for (File file : regularFiles) {
        String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
        html.append("<li class=\"file-item\">");
        html.append("<a href=\"/").append(path).append("\" download>");
        html.append("<span class=\"icon\">").append(getFileIcon(file.getName())).append("</span>");
        html.append("<span class=\"name\">").append(escapeHtml(file.getName())).append("</span>");
        html.append("<span class=\"size\">")
            .append(formatFileSize(file.length()))
            .append("</span>");
        html.append("</a></li>");
      }

      html.append("</ul>");
    }

    html.append("</div></body></html>");
    return html.toString();
  }

  /** Generate JSON for directory listing */
  private String generateDirectoryJson(File directory) {
    StringBuilder json = new StringBuilder();
    json.append("{\"path\":\"").append(escapeJson(directory.getAbsolutePath())).append("\",");
    json.append("\"name\":\"").append(escapeJson(directory.getName())).append("\",");
    json.append("\"files\":[");

    File[] files = directory.listFiles();
    if (files != null) {
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        json.append("{");
        json.append("\"name\":\"").append(escapeJson(file.getName())).append("\",");
        json.append("\"type\":\"").append(file.isDirectory() ? "directory" : "file").append("\",");
        json.append("\"size\":").append(file.length()).append(",");
        json.append("\"modified\":").append(file.lastModified());
        json.append("}");

        if (i < files.length - 1) {
          json.append(",");
        }
      }
    }

    json.append("]}");
    return json.toString();
  }

  /** Get embedded CSS */
  private String getEmbeddedCss() {
    return """
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        min-height: 100vh; padding: 20px;
    }
    .container {
        max-width: 900px; margin: 0 auto; background: white;
        border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.15);
        overflow: hidden;
    }
    .header {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white; padding: 40px 30px; text-align: center;
        position: relative;
    }
    .header h1 { font-size: 32px; margin-bottom: 8px; font-weight: 600; }
    .subtitle { opacity: 0.95; font-size: 15px; }
    .security-badge {
        position: absolute; top: 15px; right: 20px;
        background: rgba(255,255,255,0.2); padding: 6px 12px;
        border-radius: 20px; font-size: 12px; font-weight: 600;
        backdrop-filter: blur(10px);
    }
    .breadcrumb {
        padding: 16px 25px; background: #f8f9fa;
        border-bottom: 1px solid #e9ecef; font-size: 14px;
    }
    .breadcrumb a { color: #667eea; text-decoration: none; font-weight: 500; }
    .breadcrumb a:hover { text-decoration: underline; }
    .file-list { list-style: none; }
    .file-item {
        border-bottom: 1px solid #e9ecef;
        transition: all 0.2s ease;
    }
    .file-item:hover { background: #f8f9fa; transform: translateX(4px); }
    .file-item a {
        display: flex; align-items: center;
        padding: 18px 25px; text-decoration: none;
        color: #2d3748;
    }
    .icon { font-size: 28px; margin-right: 16px; min-width: 32px; }
    .name { flex: 1; font-weight: 500; font-size: 15px; }
    .size {
        color: #718096; font-size: 13px;
        margin-left: 12px; min-width: 70px;
        text-align: right;
    }
    .empty {
        text-align: center; padding: 80px 20px;
        color: #a0aec0; font-size: 18px;
    }
    @media (max-width: 600px) {
        .container { border-radius: 0; }
        .header h1 { font-size: 24px; }
        .size { display: none; }
        .security-badge { position: static; display: block; margin-top: 10px; }
    }
    """;
  }

  // Utility methods
  public int getActualPort() {
    return actualPort;
  }

  public boolean isSecure() {
    return useHttps;
  }

  public ConnectionMonitor getConnectionMonitor() {
    return connectionMonitor;
  }

  public ConnectionMonitor.ConnectionStats getConnectionStats() {
    return connectionMonitor.getStats();
  }

  @Override
  public void stop() {
    super.stop();
    if (connectionMonitor != null) {
      connectionMonitor.shutdown();
    }
    logger.info("Server stopped");
  }

  private String getMimeType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
    if (lower.endsWith(".txt")) return "text/plain";
    if (lower.endsWith(".css")) return "text/css";
    if (lower.endsWith(".js")) return "application/javascript";
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".xml")) return "application/xml";
    if (lower.matches(".*\\.(jpg|jpeg)$")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".svg")) return "image/svg+xml";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.matches(".*\\.(zip|rar|7z)$")) return "application/zip";
    if (lower.endsWith(".mp4")) return "video/mp4";
    if (lower.endsWith(".mp3")) return "audio/mpeg";
    return "application/octet-stream";
  }

  private String getFileIcon(String filename) {
    String lower = filename.toLowerCase();
    if (lower.matches(".*\\.(jpg|jpeg|png|gif|svg|bmp|webp)$")) return "🖼️";
    if (lower.matches(".*\\.(mp4|avi|mov|mkv)$")) return "🎬";
    if (lower.matches(".*\\.(mp3|wav|flac|aac)$")) return "🎵";
    if (lower.endsWith(".pdf")) return "📄";
    if (lower.matches(".*\\.(zip|rar|7z|tar|gz)$")) return "📦";
    if (lower.matches(".*\\.(doc|docx)$")) return "📝";
    if (lower.matches(".*\\.(xls|xlsx|csv)$")) return "📊";
    if (lower.matches(".*\\.(ppt|pptx)$")) return "📽️";
    if (lower.endsWith(".txt")) return "📃";
    if (lower.matches(".*\\.(html|css|js|java|py)$")) return "💻";
    return "📄";
  }

  private String formatFileSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }

  private String escapeHtml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String escapeJson(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
