package com.smartreceipt.controller;

import com.smartreceipt.dto.ReceiptRequest;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.security.UserPrincipal;
import com.smartreceipt.service.OcrService;
import com.smartreceipt.service.PdfService;
import com.smartreceipt.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipt Management", description = "Receipt CRUD, OCR upload, and PDF download endpoints")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final OcrService ocrService;
    private final PdfService pdfService;

    @PostMapping
    @Operation(summary = "Create receipt manually", description = "Saves a new receipt manually provided by the authenticated user.")
    @ApiResponse(responseCode = "201", description = "Receipt created successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<ReceiptResponse> createReceipt(
            @Valid @RequestBody ReceiptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReceiptResponse response = receiptService.createReceipt(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all receipts", description = "Returns user receipts. ADMIN receives all receipts across all users.")
    @ApiResponse(responseCode = "200", description = "Receipt list retrieved")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<List<ReceiptResponse>> getAllReceipts(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<ReceiptResponse> response = receiptService.getAllReceipts(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get receipt by ID", description = "Returns a single receipt. USER can only access their own receipts.")
    @ApiResponse(responseCode = "200", description = "Receipt retrieved")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    public ResponseEntity<ReceiptResponse> getReceiptById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReceiptResponse response = receiptService.getReceiptById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update receipt", description = "Updates an existing receipt. USER can only update their own receipts.")
    @ApiResponse(responseCode = "200", description = "Receipt updated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    public ResponseEntity<ReceiptResponse> updateReceipt(
            @PathVariable String id,
            @Valid @RequestBody ReceiptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReceiptResponse response = receiptService.updateReceipt(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete receipt", description = "Deletes a receipt by ID. USER can only delete their own receipts.")
    @ApiResponse(responseCode = "204", description = "Receipt deleted")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    public ResponseEntity<Void> deleteReceipt(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        receiptService.deleteReceipt(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload receipt image or PDF for OCR parsing", description = "Accepts JPG, JPEG, PNG, or PDF file, runs Tesseract OCR, parses details, saves receipt in MongoDB.")
    @ApiResponse(responseCode = "201", description = "Receipt parsed and created via OCR")
    @ApiResponse(responseCode = "400", description = "Invalid file or OCR error")
    public ResponseEntity<ReceiptResponse> uploadReceipt(
            @Parameter(description = "Receipt image or PDF file") @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("OCR upload request received");
        try {
            ReceiptResponse response = ocrService.processReceiptUpload(file, currentUser);
            log.info("OCR upload response returned");
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("OCR processing failed", e);
            throw e;
        }
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download receipt PDF report", description = "Generates a downloadable PDF summary document for the requested receipt.")
    @ApiResponse(responseCode = "200", description = "PDF generated successfully", content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Receipt receipt = receiptService.findReceiptEntityById(id, currentUser);
        byte[] pdfBytes = pdfService.generateReceiptPdf(receipt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "receipt_" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
