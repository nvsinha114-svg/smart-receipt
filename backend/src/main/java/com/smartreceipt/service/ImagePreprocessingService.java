package com.smartreceipt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ImagePreprocessingService {

    /**
     * Named preprocessed image variant for multi-pass OCR.
     */
    public static class ImageVariant {
        private final String name;
        private final BufferedImage image;

        public ImageVariant(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
        }

        public String getName() {
            return name;
        }

        public BufferedImage getImage() {
            return image;
        }
    }

    /**
     * Generates a prioritized list of preprocessed image variants for multi-pass OCR.
     */
    public List<ImageVariant> generatePreprocessingVariants(BufferedImage original) {
        List<ImageVariant> variants = new ArrayList<>();
        if (original == null) {
            return variants;
        }

        // Always include original first as baseline and ultimate safety fallback
        variants.add(new ImageVariant("Original", original));

        try {
            // 1. Orientation correction
            BufferedImage oriented = autoCorrectOrientation(original);
            if (oriented != original) {
                variants.add(new ImageVariant("Oriented", oriented));
            }

            // 2. Camera photo document boundary auto-crop
            BufferedImage cropped = autoCropReceiptBackground(oriented);

            // 3. Resolution check & upscaling
            BufferedImage scaled = upscaleIfLowResolution(cropped);

            // 4. Variant A: Grayscale + Luminance-based Contrast Adjustment
            BufferedImage variantA = enhanceContrastAndGrayscale(scaled);
            if (variantA != null) {
                variants.add(new ImageVariant("Grayscale_Contrast", variantA));
            }

            // 5. Variant B: Adaptive Binarization / Thresholding
            BufferedImage variantB = binarizeAdaptive(variantA != null ? variantA : scaled);
            if (variantB != null) {
                variants.add(new ImageVariant("Adaptive_Threshold", variantB));
            }

            // 6. Variant C: Sharpening (for blurry camera photos)
            BufferedImage variantC = sharpenImage(variantA != null ? variantA : scaled);
            if (variantC != null) {
                variants.add(new ImageVariant("Sharpened", variantC));
            }

        } catch (Exception e) {
            log.warn("Non-fatal warning in image preprocessing pipeline: {}. Falling back to baseline image.", e.getMessage());
        }

        return variants;
    }

    public BufferedImage autoCorrectOrientation(BufferedImage src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();

        // Heuristic: Standard receipts are portrait format. If width > 1.35 * height, rotate 90° clockwise.
        if (width > (int) (height * 1.35)) {
            log.info("Landscape camera receipt image detected ({}x{}). Rotating 90 degrees for upright OCR alignment.", width, height);
            return rotateImage(src, 90.0);
        }
        return src;
    }

    public BufferedImage rotateImage(BufferedImage src, double angleDegrees) {
        if (src == null) return null;
        try {
            double radians = Math.toRadians(angleDegrees);
            double sin = Math.abs(Math.sin(radians));
            double cos = Math.abs(Math.cos(radians));
            int w = src.getWidth();
            int h = src.getHeight();
            int newW = (int) Math.floor(w * cos + h * sin);
            int newH = (int) Math.floor(h * cos + w * sin);

            BufferedImage rotated = new BufferedImage(newW, newH, getSafeImageType(src));
            Graphics2D g2d = rotated.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.translate((newW - w) / 2.0, (newH - h) / 2.0);
            g2d.rotate(radians, w / 2.0, h / 2.0);
            g2d.drawImage(src, 0, 0, null);
            g2d.dispose();
            return rotated;
        } catch (Exception e) {
            log.warn("Failed to rotate image: {}", e.getMessage());
            return src;
        }
    }

    public BufferedImage autoCropReceiptBackground(BufferedImage src) {
        if (src == null || src.getWidth() < 150 || src.getHeight() < 150) {
            return src;
        }

        try {
            int width = src.getWidth();
            int height = src.getHeight();

            int top = 0;
            int bottom = height - 1;
            int left = 0;
            int right = width - 1;

            // Scan top down to locate paper top edge
            for (int y = 0; y < height / 4; y++) {
                if (isReceiptPaperContentRow(src, y)) {
                    top = y;
                    break;
                }
            }

            // Scan bottom up to locate paper bottom edge
            for (int y = height - 1; y > (height * 3) / 4; y--) {
                if (isReceiptPaperContentRow(src, y)) {
                    bottom = y;
                    break;
                }
            }

            // Scan left to right to locate paper left edge
            for (int x = 0; x < width / 4; x++) {
                if (isReceiptPaperContentColumn(src, x)) {
                    left = x;
                    break;
                }
            }

            // Scan right to left to locate paper right edge
            for (int x = width - 1; x > (width * 3) / 4; x--) {
                if (isReceiptPaperContentColumn(src, x)) {
                    right = x;
                    break;
                }
            }

            int cropW = right - left + 1;
            int cropH = bottom - top + 1;

            // Ensure cropped region preserves at least 65% of dimensions before applying
            if (cropW > width * 0.65 && cropH > height * 0.65 && (cropW < width || cropH < height)) {
                log.info("Cropped camera background margins: [left={}, top={}, width={}, height={}]", left, top, cropW, cropH);
                return src.getSubimage(left, top, cropW, cropH);
            }
        } catch (Exception e) {
            log.warn("Auto-cropping receipt background warning: {}", e.getMessage());
        }

        return src;
    }

    private boolean isReceiptPaperContentRow(BufferedImage img, int y) {
        int width = img.getWidth();
        int brightCount = 0;
        int totalSampled = 0;
        for (int x = 0; x < width; x += 6) {
            int rgb = img.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            if (luminance > 115) { // Paper surfaces reflect light higher than surrounding dark table/background
                brightCount++;
            }
            totalSampled++;
        }
        return totalSampled > 0 && ((double) brightCount / totalSampled) > 0.35;
    }

    private boolean isReceiptPaperContentColumn(BufferedImage img, int x) {
        int height = img.getHeight();
        int brightCount = 0;
        int totalSampled = 0;
        for (int y = 0; y < height; y += 6) {
            int rgb = img.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            if (luminance > 115) {
                brightCount++;
            }
            totalSampled++;
        }
        return totalSampled > 0 && ((double) brightCount / totalSampled) > 0.35;
    }

    public BufferedImage upscaleIfLowResolution(BufferedImage src) {
        if (src == null) return null;
        int maxDimension = Math.max(src.getWidth(), src.getHeight());
        int minDimension = Math.min(src.getWidth(), src.getHeight());

        if (maxDimension < 1500 || minDimension < 1000) {
            double scaleW = 1500.0 / src.getWidth();
            double scaleH = 1500.0 / src.getHeight();
            double scale = Math.max(scaleW, scaleH);
            scale = Math.min(scale, 3.0); // Cap scaling to 3.0x to preserve memory

            if (scale > 1.05) {
                int targetW = (int) Math.round(src.getWidth() * scale);
                int targetH = (int) Math.round(src.getHeight() * scale);
                log.info("Upscaling low-resolution receipt image from {}x{} to {}x{} (scale factor: {})",
                        src.getWidth(), src.getHeight(), targetW, targetH, String.format("%.2f", scale));

                BufferedImage scaled = new BufferedImage(targetW, targetH, getSafeImageType(src));
                Graphics2D g2d = scaled.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(src, 0, 0, targetW, targetH, null);
                g2d.dispose();
                return scaled;
            }
        }
        return src;
    }

    public BufferedImage enhanceContrastAndGrayscale(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();

        // 1. Convert to grayscale
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // 2. Measure mean luminance
        long totalLuminance = 0;
        int sampledPixels = 0;
        Raster raster = gray.getRaster();
        int[] pixel = new int[1];
        for (int y = 0; y < h; y += 5) {
            for (int x = 0; x < w; x += 5) {
                raster.getPixel(x, y, pixel);
                totalLuminance += pixel[0];
                sampledPixels++;
            }
        }
        double meanLuminance = sampledPixels > 0 ? (double) totalLuminance / sampledPixels : 128.0;

        float scaleFactor = 1.25f;
        float offset = 0.0f;

        if (meanLuminance < 95) { // Dark image
            scaleFactor = 1.45f;
            offset = 30.0f;
            log.info("Dark image detected (mean luminance={}). Applying brightness and contrast boost.", (int) meanLuminance);
        } else if (meanLuminance > 205) { // Overexposed image
            scaleFactor = 1.15f;
            offset = -25.0f;
            log.info("Overexposed image detected (mean luminance={}). Reducing exposure glare.", (int) meanLuminance);
        }

        try {
            RescaleOp rescale = new RescaleOp(scaleFactor, offset, null);
            BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            rescale.filter(gray, dest);
            return dest;
        } catch (Exception e) {
            log.warn("Contrast enhancement warning: {}. Returning gray image.", e.getMessage());
            return gray;
        }
    }

    public BufferedImage binarizeAdaptive(BufferedImage graySrc) {
        if (graySrc == null) return null;
        int w = graySrc.getWidth();
        int h = graySrc.getHeight();

        try {
            BufferedImage binarized = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            Raster srcRaster = graySrc.getRaster();
            WritableRaster destRaster = binarized.getRaster();

            // Calculate overall average luminance
            long sum = 0;
            int count = 0;
            int[] pixel = new int[1];
            for (int y = 0; y < h; y += 6) {
                for (int x = 0; x < w; x += 6) {
                    srcRaster.getPixel(x, y, pixel);
                    sum += pixel[0];
                    count++;
                }
            }
            int threshold = count > 0 ? (int) (sum / count) - 10 : 120;
            threshold = Math.max(75, Math.min(195, threshold));

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    srcRaster.getPixel(x, y, pixel);
                    if (pixel[0] < threshold) {
                        destRaster.setPixel(x, y, new int[]{0}); // Dark text pixel
                    } else {
                        destRaster.setPixel(x, y, new int[]{255}); // Light background pixel
                    }
                }
            }
            return binarized;
        } catch (Exception e) {
            log.warn("Adaptive binarization warning: {}", e.getMessage());
            return graySrc;
        }
    }

    public BufferedImage sharpenImage(BufferedImage src) {
        if (src == null) return null;
        try {
            float[] sharpenKernel = {
                    0.0f, -0.7f, 0.0f,
                    -0.7f, 3.8f, -0.7f,
                    0.0f, -0.7f, 0.0f
            };
            Kernel kernel = new Kernel(3, 3, sharpenKernel);
            ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), getSafeImageType(src));
            convolveOp.filter(src, dest);
            return dest;
        } catch (Exception e) {
            log.warn("Sharpening warning: {}", e.getMessage());
            return src;
        }
    }

    private int getSafeImageType(BufferedImage img) {
        int type = img.getType();
        return (type == BufferedImage.TYPE_CUSTOM || type == 0) ? BufferedImage.TYPE_INT_RGB : type;
    }
}
