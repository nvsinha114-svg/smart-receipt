package com.smartreceipt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImagePreprocessingServiceTest {

    private ImagePreprocessingService preprocessingService;

    @BeforeEach
    void setUp() {
        preprocessingService = new ImagePreprocessingService();
    }

    @Test
    @DisplayName("Should generate preprocessing variants for normal receipt image")
    void generatePreprocessingVariants_NormalImage() {
        BufferedImage img = createSampleReceiptImage(800, 1000, Color.WHITE, Color.BLACK);
        List<ImagePreprocessingService.ImageVariant> variants = preprocessingService.generatePreprocessingVariants(img);

        assertNotNull(variants);
        assertTrue(variants.size() >= 3);
        assertEquals("Original", variants.get(0).getName());
    }

    @Test
    @DisplayName("Should detect landscape aspect ratio and auto-rotate 90 degrees")
    void autoCorrectOrientation_LandscapeImage() {
        BufferedImage landscapeImg = createSampleReceiptImage(1200, 600, Color.WHITE, Color.BLACK);
        BufferedImage rotated = preprocessingService.autoCorrectOrientation(landscapeImg);

        assertNotNull(rotated);
        assertTrue(rotated.getHeight() > rotated.getWidth(), "Rotated image should be portrait format");
        assertEquals(1200, rotated.getHeight());
        assertEquals(600, rotated.getWidth());
    }

    @Test
    @DisplayName("Should detect dark image and boost brightness/contrast")
    void enhanceContrastAndGrayscale_DarkImage() {
        BufferedImage darkImg = createSampleReceiptImage(500, 800, new Color(40, 40, 40), Color.WHITE);
        BufferedImage enhanced = preprocessingService.enhanceContrastAndGrayscale(darkImg);

        assertNotNull(enhanced);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, enhanced.getType());
    }

    @Test
    @DisplayName("Should upscale low-resolution image to minimum dimension threshold")
    void upscaleIfLowResolution_LowResImage() {
        BufferedImage lowRes = createSampleReceiptImage(300, 450, Color.WHITE, Color.BLACK);
        BufferedImage scaled = preprocessingService.upscaleIfLowResolution(lowRes);

        assertNotNull(scaled);
        assertTrue(scaled.getWidth() > 300);
        assertTrue(scaled.getHeight() > 450);
    }

    @Test
    @DisplayName("Should sharpen blurry image using convolution matrix")
    void sharpenImage_BlurryImage() {
        BufferedImage sample = createSampleReceiptImage(600, 800, Color.LIGHT_GRAY, Color.DARK_GRAY);
        BufferedImage sharpened = preprocessingService.sharpenImage(sample);

        assertNotNull(sharpened);
        assertEquals(600, sharpened.getWidth());
        assertEquals(800, sharpened.getHeight());
    }

    @Test
    @DisplayName("Should safely handle null and empty image inputs")
    void safeNullHandling() {
        assertNull(preprocessingService.autoCorrectOrientation(null));
        assertNull(preprocessingService.upscaleIfLowResolution(null));
        assertNull(preprocessingService.enhanceContrastAndGrayscale(null));
        assertNull(preprocessingService.binarizeAdaptive(null));
        assertNull(preprocessingService.sharpenImage(null));
        assertTrue(preprocessingService.generatePreprocessingVariants(null).isEmpty());
    }

    private BufferedImage createSampleReceiptImage(int width, int height, Color bgColor, Color textColor) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(textColor);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("STORE RECEIPT", 50, 50);
        g2d.drawString("Item 1: ₹500.00", 50, 100);
        g2d.drawString("Total: ₹500.00", 50, 150);
        g2d.dispose();
        return img;
    }
}
