package com.htshare.server;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileServer extends NanoHTTPD {
  private static final Logger logger = LoggerFactory.getLogger(FileServer.class);
  private final File rootDirectory;

  public FileServer(int port, File rootDirectory) throws IOException {
    super(port);
    this.rootDirectory = rootDirectory;

    if (!rootDirectory.exists() || !rootDirectory.isDirectory()) {
      throw new IOException("Invalid root directory: " + rootDirectory);
    }

    logger.info("File server initialized on port {} with root: {}", port, rootDirectory);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    logger.info("Request: {} {}", session.getMethod(), uri);

    try {
      // Decode URI
      uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);

      // Remove leading slash
      if (uri.startsWith("/")) {
        uri = uri.substring(1);
      }

      // Handle root or directory listing
      if (uri.isEmpty() || uri.equals("/")) {
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

  private Response serveFile(File file) throws IOException {
    String mimeType = getMimeType(file.getName());
    FileInputStream fis = new FileInputStream(file);

    Response response = newChunkedResponse(Response.Status.OK, mimeType, fis);
    response.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
    response.addHeader("Accept-Ranges", "bytes");

    logger.info("Serving file: {} ({})", file.getName(), file.length());
    return response;
  }

  private Response serveDirectoryListing(File directory, String relativePath) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head>");
    html.append("<meta charset='UTF-8'>");
    html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
    html.append("<title>File Share - ").append(directory.getName()).append("</title>");
    html.append("<style>");
    html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
    html.append(
        "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu,"
            + " sans-serif;");
    html.append("  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);");
    html.append("  min-height: 100vh; padding: 20px; }");
    html.append(".container { max-width: 800px; margin: 0 auto; background: white;");
    html.append(
        "  border-radius: 12px; box-shadow: 0 8px 32px rgba(0,0,0,0.1); overflow: hidden; }");
    html.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);");
    html.append("  color: white; padding: 30px; text-align: center; }");
    html.append(".header h1 { font-size: 28px; margin-bottom: 8px; }");
    html.append(".header p { opacity: 0.9; font-size: 14px; }");
    html.append(
        ".breadcrumb { padding: 15px 20px; background: #f8f9fa; border-bottom: 1px solid #e9ecef;");
    html.append("  font-size: 14px; color: #666; }");
    html.append(".file-list { list-style: none; }");
    html.append(".file-item { border-bottom: 1px solid #e9ecef; transition: background 0.2s; }");
    html.append(".file-item:hover { background: #f8f9fa; }");
    html.append(".file-item a { display: flex; align-items: center; padding: 16px 20px;");
    html.append("  text-decoration: none; color: #333; }");
    html.append(".file-icon { font-size: 24px; margin-right: 12px; }");
    html.append(".file-name { flex: 1; font-weight: 500; }");
    html.append(".file-size { color: #999; font-size: 13px; margin-left: 10px; }");
    html.append(".empty { text-align: center; padding: 60px 20px; color: #999; }");
    html.append("@media (max-width: 600px) { .container { border-radius: 0; }");
    html.append("  .header h1 { font-size: 22px; } }");
    html.append("</style></head><body>");

    html.append("<div class='container'>");
    html.append("<div class='header'>");
    html.append("<h1>📁 File Share</h1>");
    html.append("<p>").append(directory.getName()).append("</p>");
    html.append("</div>");

    // Breadcrumb
    if (!relativePath.isEmpty()) {
      html.append("<div class='breadcrumb'>");
      html.append("<a href='/'>🏠 Home</a>");
      String[] parts = relativePath.split("/");
      String currentPath = "";
      for (String part : parts) {
        if (!part.isEmpty()) {
          currentPath += "/" + part;
          html.append(" / <a href='").append(currentPath).append("'>").append(part).append("</a>");
        }
      }
      html.append("</div>");
    }

    File[] files = directory.listFiles();

    if (files == null || files.length == 0) {
      html.append("<div class='empty'>📭 This folder is empty</div>");
    } else {
      // Sort: directories first, then files
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

      html.append("<ul class='file-list'>");

      // Parent directory link
      if (!relativePath.isEmpty()) {
        String parentPath =
            relativePath.contains("/")
                ? relativePath.substring(0, relativePath.lastIndexOf("/"))
                : "";
        html.append("<li class='file-item'><a href='/").append(parentPath).append("'>");
        html.append("<span class='file-icon'>⬆️</span>");
        html.append("<span class='file-name'>..</span>");
        html.append("</a></li>");
      }

      // Directories
      for (File dir : dirs) {
        String path = relativePath.isEmpty() ? dir.getName() : relativePath + "/" + dir.getName();
        html.append("<li class='file-item'><a href='/").append(path).append("'>");
        html.append("<span class='file-icon'>📁</span>");
        html.append("<span class='file-name'>").append(dir.getName()).append("</span>");
        html.append("</a></li>");
      }

      // Files
      for (File file : regularFiles) {
        String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
        html.append("<li class='file-item'><a href='/").append(path).append("' download>");
        html.append("<span class='file-icon'>")
            .append(getFileIcon(file.getName()))
            .append("</span>");
        html.append("<span class='file-name'>").append(file.getName()).append("</span>");
        html.append("<span class='file-size'>")
            .append(formatFileSize(file.length()))
            .append("</span>");
        html.append("</a></li>");
      }

      html.append("</ul>");
    }

    html.append("</div></body></html>");

    return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
  }

  private String getMimeType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
    if (lower.endsWith(".txt")) return "text/plain";
    if (lower.endsWith(".css")) return "text/css";
    if (lower.endsWith(".js")) return "application/javascript";
    if (lower.endsWith(".json")) return "application/json";
    if (lower.endsWith(".xml")) return "application/xml";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".svg")) return "image/svg+xml";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".zip")) return "application/zip";
    if (lower.endsWith(".mp4")) return "video/mp4";
    if (lower.endsWith(".mp3")) return "audio/mpeg";
    return "application/octet-stream";
  }

  private String getFileIcon(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".gif")
        || lower.endsWith(".svg")) return "🖼️";
    if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov")) return "🎬";
    if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) return "🎵";
    if (lower.endsWith(".pdf")) return "📄";
    if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "📦";
    if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📝";
    if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📊";
    if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📽️";
    if (lower.endsWith(".txt")) return "📃";
    if (lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js")) return "💻";
    return "📄";
  }

  private String formatFileSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }
}
