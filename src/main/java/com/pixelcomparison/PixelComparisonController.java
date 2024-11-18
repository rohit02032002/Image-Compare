package com.pixelcomparison;

import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/pixel-compare")
public class PixelComparisonController {

    @PostMapping("/compare-pdf-images")
    public ResponseEntity<byte[]> pixelCompare(
            @RequestParam("pdf1") MultipartFile pdf1,
            @RequestParam("pdf2") MultipartFile pdf2) {

        try {
            // Extract images from both PDFs
            List<BufferedImage> imagesFromPdf1 = extractImagesFromPdf(pdf1);
            List<BufferedImage> imagesFromPdf2 = extractImagesFromPdf(pdf2);

            // Validate that at least one image exists in both PDFs
            if (imagesFromPdf1.isEmpty() || imagesFromPdf2.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }

            // Compare the first image from both PDFs
            BufferedImage img1 = imagesFromPdf1.get(0); // First image from pdf1
            BufferedImage img2 = imagesFromPdf2.get(0); // First image from pdf2

            // Perform pixel comparison
            BufferedImage comparisonResult = compareImages(img1, img2);

            // Convert result image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(comparisonResult, "png", baos);
            byte[] comparisonResultBytes = baos.toByteArray();

            // Set response headers for image content
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "image/png");

            return new ResponseEntity<>(comparisonResultBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Extracts all images from a PDF file.
     *
     * @param pdfFile The PDF file as a MultipartFile.
     * @return A list of BufferedImages extracted from the PDF.
     * @throws IOException If an error occurs while reading the PDF.
     */
    private List<BufferedImage> extractImagesFromPdf(MultipartFile pdfFile) throws IOException {
        List<BufferedImage> images = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile.getInputStream())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                PDPage pdPage = document.getPage(page);
                PDResources resources = pdPage.getResources();

                for (COSName cosName : resources.getXObjectNames()) {
                    if (resources.isImageXObject(cosName)) {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(cosName);
                        images.add(image.getImage());
                    }
                }

                // Render the full page as a fallback if no images are detected
                if (images.isEmpty()) {
                    images.add(renderer.renderImageWithDPI(page, 300)); // High quality render
                }
            }
        }

        return images;
    }

    /**
     * Compares two images using pixel comparison.
     *
     * @param img1 The first image.
     * @param img2 The second image.
     * @return The resulting comparison image.
     */
    private BufferedImage compareImages(BufferedImage img1, BufferedImage img2) {
        // Where to save the result image
        File resultDestination = new File("result.png");

        // Perform image comparison
        ImageComparison imageComparison = new ImageComparison(img1, img2, resultDestination);

        // Optional: Configure comparison settings
        imageComparison.setThreshold(10);
        imageComparison.setRectangleLineWidth(5);
        imageComparison.setMaximalRectangleCount(10);
        imageComparison.setMinimalRectangleSize(100);

        // Perform the comparison
        ImageComparisonResult comparisonResult = imageComparison.compareImages();

        // Save the result image
        ImageComparisonUtil.saveImage(resultDestination, comparisonResult.getResult());

        return comparisonResult.getResult();
    }
}
