package com.htshare.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QRCodeGenerator {
  private static final Logger logger = LoggerFactory.getLogger(QRCodeGenerator.class);

  /**
   * Generate a QR code image from the given text
   *
   * @param text The text to encode (e.g., URL)
   * @param width Width of the QR code image
   * @param height Height of the QR code image
   * @return JavaFX Image containing the QR code
   */
  public static Image generateQRCode(String text, int width, int height) throws WriterException {
    logger.info("Generating QR code for: {}", text);

    // Configure QR code parameters
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(EncodeHintType.MARGIN, 1); // Smaller margin for better appearance

    // Generate QR code matrix
    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

    // Convert BitMatrix to JavaFX Image
    WritableImage image = new WritableImage(width, height);
    PixelWriter pixelWriter = image.getPixelWriter();

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        Color color = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
        pixelWriter.setColor(x, y, color);
      }
    }

    logger.info("QR code generated successfully ({}x{})", width, height);
    return image;
  }

  /** Generate a QR code with custom colors */
  public static Image generateQRCodeWithColors(
      String text, int width, int height, Color foreground, Color background)
      throws WriterException {
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(EncodeHintType.MARGIN, 1);

    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

    WritableImage image = new WritableImage(width, height);
    PixelWriter pixelWriter = image.getPixelWriter();

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        Color color = bitMatrix.get(x, y) ? foreground : background;
        pixelWriter.setColor(x, y, color);
      }
    }

    return image;
  }
}
