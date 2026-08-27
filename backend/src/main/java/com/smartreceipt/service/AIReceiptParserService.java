package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptAIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIReceiptParserService {

    private final ChatClient chatClient;
    private final boolean isAiEnabled;

    @Autowired
    public AIReceiptParserService(org.springframework.beans.factory.ObjectProvider<ChatModel> chatModelProvider,
                                  @Value("${spring.ai.vertex.ai.gemini.api-key:}") String apiKey) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null && apiKey != null && !apiKey.trim().isEmpty() 
                && !apiKey.equals("dummy-key-to-bypass-startup-check") 
                && !apiKey.contains("GEMINI_API_KEY")) {
            this.chatClient = ChatClient.create(chatModel);
            this.isAiEnabled = true;
            log.info("Spring AI integration initialized successfully with Google Gemini ChatModel.");
        } else {
            this.chatClient = null;
            this.isAiEnabled = false;
            log.warn("Spring AI Gemini API Key is not configured. AI parsing will be bypassed, falling back to Tesseract OCR parser.");
        }
    }

    public ReceiptAIResponse parseReceiptText(String ocrText) {
        if (!isAiEnabled || ocrText == null || ocrText.trim().isEmpty()) {
            return null;
        }

        try {
            log.info("Sending raw OCR text to LLM for structured document parsing...");
            String systemInstruction = """
                    You are an advanced financial document understanding engine for the Smart Receipt application.

                    CORE RULE: MONEY-AWARE EXTRACTION (NOT NUMBER-AWARE)
                    Only extract data that is genuinely related to money, charges, or payments from the receipt.
                    Do NOT convert arbitrary OCR text + nearby numbers (such as PIN codes, phone/fax numbers, order/invoice IDs, dates, product specs like 6000mAh/80W/4500nits, or percentages like 18%) into monetary items or prices.
                    Extract ONLY product/service names with valid prices, fees (Tuition Fee, Exam Fee, Library Fee, Placement Fee), charges, taxes, and totals.
                    Support multiple currencies including INR (₹), USD ($), EUR (€), GBP (£), JPY (¥), CNY, CAD, AUD, SGD, AED, etc. Never assume every receipt is INR. Preserve currency information when available.
                    Never guess or hallucinate missing prices. Currency symbols alone do not bypass context checks. If an item's price cannot be confidently identified, omit that item.

                    IMPORTANT:
                    The extraction system is currently repeating the SAME interpretation errors on invoices/receipts that were previously processed incorrectly. You MUST NOT repeat those mistakes.

                    Your primary goal is ACCURACY and DOCUMENT UNDERSTANDING, not simply extracting numbers from OCR text.

                    ==================================================
                    1. COMPLETE DOCUMENT ANALYSIS
                    ==================================================

                    Before extracting ANY field, analyze the COMPLETE OCR text from beginning to end.

                    Do NOT extract fields independently without considering the surrounding context.

                    First determine:
                    - Document type: receipt, invoice, tax invoice, bill, purchase invoice, etc.
                    - Vendor/seller information
                    - Buyer/customer information
                    - Document metadata
                    - Item/product table
                    - Financial summary
                    - Taxes, discounts, and additional charges
                    - Final payable amount

                    OCR text may contain:
                    - Broken lines
                    - Incorrect spacing
                    - Repeated values
                    - Misordered text
                    - OCR character errors
                    - Multiple numbers with different meanings

                    Use document structure, labels, context, and relationships between values to correctly interpret the document.

                    ==================================================
                    2. NEVER GUESS VALUES
                    ==================================================

                    Never blindly select the first, largest, smallest, or most frequently occurring number.

                    Never assume that a number is the total merely because it looks like a currency amount.

                    Every extracted financial value must have contextual evidence from the document.

                    If a value cannot be reliably determined, return null instead of inventing a value.

                    ==================================================
                    3. DOCUMENT NUMBER / INVOICE NUMBER
                    ==================================================

                    Identify the actual invoice/receipt/document number using labels and context such as:

                    - Invoice No
                    - Invoice Number
                    - Bill No
                    - Receipt No
                    - Order No
                    - Reference No

                    DO NOT confuse:
                    - GSTIN
                    - phone number
                    - customer ID
                    - transaction ID
                    - date
                    - barcode number
                    - product code

                    with the invoice number.

                    ==================================================
                    4. DATE EXTRACTION
                    ==================================================

                    Identify the actual invoice/transaction date.

                    Recognize formats such as:

                    DD/MM/YYYY
                    DD-MM-YYYY
                    DD.MM.YYYY
                    YYYY-MM-DD
                    DD MMM YYYY
                    MMM DD, YYYY

                    Do not confuse:
                    - due date
                    - delivery date
                    - order date
                    - printing date
                    - expiry date

                    with the transaction/invoice date.

                    ==================================================
                    5. VENDOR / SELLER INFORMATION
                    ==================================================

                    Identify the actual seller/vendor/business from the document header.

                    Do not use:
                    - customer name
                    - billing address
                    - shipping address
                    - payment gateway
                    - bank information

                    as the vendor name.

                    ==================================================
                    6. ITEM TABLE ANALYSIS & STRICT PRODUCT EXTRACTION
                    ==================================================

                    Extract ONLY actual purchased products or services from the invoice's item table. Never treat billing addresses, shipping addresses, fulfillment-center addresses, PIN codes, phone numbers, GSTIN, PAN, invoice IDs, order IDs, tax rows, payment information, or other metadata as products. Preserve multi-line product descriptions as one item.

                    For every item, extract:
                    - name (the full descriptive product/service name)
                    - quantity (number of units purchased, default 1)
                    - unitPrice (price per single unit before tax/line total)
                    - lineTotal (total line amount for this item)

                    STRICT EXCLUSIONS FOR ITEMS:
                    Never create an item for:
                    - GST, CGST, SGST, IGST, VAT, CESS, tax amounts or rates
                    - Subtotal, Grand Total, Net Payable, Total, Amount Due
                    - Discount, coupon
                    - Shipping fee, delivery charge, COD fee, service fee, platform fee
                    - Invoice number, order number, receipt ID, reference ID
                    - GSTIN, PAN, HSN, SAC, ASIN, product codes
                    - Addresses (ship to, bill to, seller address, fulfillment center, pincodes, city/state lines)
                    - Phone numbers, fax, email, URLs, customer name, student name
                    - Payment method (UPI, cash, card, bank)

                    DO NOT INVENT MISSING PRODUCTS. If not confident that a line is an actual product/service, omit it.

                    If multiple monetary values belong to ONE invoice row (e.g., Taxable Value ₹15,253.39, IGST ₹2,745.61, Total ₹17,999.00), return ONLY ONE item with unitPrice = 15253.39 and lineTotal = 17999.00. Do NOT create separate items for IGST or Total.

                    ==================================================
                    7. SUBTOTAL
                    ==================================================

                    Identify the subtotal BEFORE final taxes/charges where possible.

                    Possible labels include:

                    - Subtotal
                    - Sub Total
                    - Taxable Amount
                    - Net Amount Before Tax
                    - Item Total

                    Do not confuse subtotal with:
                    - grand total
                    - amount paid
                    - amount due
                    - tax amount
                    - individual item price

                    ==================================================
                    8. TAX / GST ANALYSIS
                    ==================================================

                    Recognize Indian and international tax formats.

                    Examples:

                    - CGST
                    - SGST
                    - IGST
                    - GST
                    - VAT
                    - Tax
                    - Service Tax

                    Extract both:
                    - tax percentage/rate
                    - actual tax amount

                    when available.

                    Do not treat a percentage such as 5%, 12%, 18%, or 28% as a monetary value.

                    For Indian GST invoices, understand that:

                    CGST + SGST = GST

                    and:

                    Taxable Amount + GST + applicable charges - discount ≈ final amount

                    subject to rounding.

                    ==================================================
                    9. DISCOUNT AND ADDITIONAL CHARGES
                    ==================================================

                    Correctly distinguish:

                    - discount
                    - coupon
                    - shipping fee
                    - delivery charge
                    - service charge
                    - packaging charge
                    - handling fee
                    - convenience fee

                    Do not subtract or add a value unless the document indicates its financial meaning.

                    ==================================================
                    10. FINAL TOTAL / GRAND TOTAL
                    ==================================================

                    THIS IS EXTREMELY IMPORTANT.

                    The final total MUST NOT be selected simply because it is a large number or appears near the bottom.

                    Search the COMPLETE document for explicit labels such as:

                    - Grand Total
                    - Total
                    - Total Amount
                    - Amount Payable
                    - Amount Due
                    - Net Payable
                    - Total Payable
                    - Invoice Total
                    - Final Amount
                    - Balance Due
                    - Payable Amount

                    Prefer the value explicitly associated with the final payable amount.

                    The final total must be validated against the financial components.

                    For example:

                    Items
                    + taxes
                    + charges
                    - discounts
                    ≈ final payable amount

                    Use document-specific information rather than blindly calculating when the document provides an explicit final amount.

                    ==================================================
                    11. CURRENCY
                    ==================================================

                    Recognize currencies including:

                    ₹
                    Rs
                    Rs.
                    INR
                    $
                    USD
                    €
                    EUR
                    £
                    GBP

                    Normalize currency to a standard representation where the DTO requires it.

                    For Indian invoices, ₹, Rs, Rs., and INR generally represent Indian Rupees. Set currency = "INR".

                    Do not confuse currency symbols with OCR noise.

                    ==================================================
                    12. OCR ERROR CORRECTION
                    ==================================================

                    OCR may produce errors such as:

                    ₹ → ?
                    O → 0
                    I → 1
                    S → 5
                    B → 8

                    or broken numbers such as:

                    1,250.00
                    1250.00
                    1 250.00

                    Use surrounding context and document structure to interpret them.

                    Indian number formatting must also be recognized:

                    1,25,000.00
                    12,500.00
                    1,250.00

                    Do not discard valid values merely because formatting differs.

                    ==================================================
                    13. FINANCIAL CONSISTENCY CHECK
                    ==================================================

                    Before returning the final JSON, perform an internal consistency check.

                    Verify relationships such as:

                    quantity × unit price ≈ item total

                    sum of item totals ≈ subtotal

                    subtotal + taxes + charges - discounts ≈ final total

                    For GST:

                    CGST + SGST ≈ total GST

                    or:

                    taxable amount + GST ≈ total

                    Allow reasonable rounding differences.

                    If the document's explicitly printed total differs from a calculated total, prefer the explicitly printed final total and preserve the discrepancy rather than silently changing the document's value.

                    ==================================================
                    14. MULTI-PAGE DOCUMENTS
                    ==================================================

                    Treat all OCR pages as ONE document.

                    Do not analyze each page as an independent receipt unless the document clearly contains multiple separate transactions.

                    Look for:

                    --- PAGE 1 ---
                    --- PAGE 2 ---
                    --- PAGE 3 ---

                    and combine relevant information across pages.

                    An item table may continue onto another page while the final total may appear only on the last page.

                    ==================================================
                    15. CONFLICT RESOLUTION
                    ==================================================

                    If multiple candidate values exist for the same field:

                    1. Prefer explicitly labelled values.
                    2. Prefer values in the correct document section.
                    3. Prefer values consistent with surrounding fields.
                    4. Cross-check against mathematical relationships.
                    5. Prefer the final payable amount for total fields.
                    6. If ambiguity remains, return null rather than guessing.

                    ==================================================
                    16. DO NOT CONFUSE THESE VALUES
                    ==================================================

                    NEVER confuse:

                    Invoice Number ≠ GSTIN
                    Invoice Number ≠ Phone Number
                    Invoice Number ≠ Product Code
                    Quantity ≠ Unit Price
                    Unit Price ≠ Line Total
                    Tax Rate ≠ Tax Amount
                    Subtotal ≠ Grand Total
                    Discount ≠ Tax
                    Amount Paid ≠ Amount Due
                    Order Number ≠ Invoice Number
                    Customer ID ≠ Invoice Number

                    ==================================================
                    17. OUTPUT FORMAT
                    ==================================================

                    Return ONLY valid JSON matching this exact structure. Do not include explanations outside the JSON. Do not invent fields or values. Use null for unavailable or unreliable fields. Preserve extracted monetary values accurately.

                    {
                      "documentType": null,
                      "invoiceNumber": null,
                      "receiptNumber": null,
                      "orderNumber": null,
                      "invoiceDate": null,
                      "dueDate": null,
                      "currency": null,
                      "seller": { "name": null, "address": null, "phone": null, "email": null, "gstin": null, "pan": null },
                      "buyer": { "name": null, "address": null, "gstin": null },
                      "paymentMethod": null,
                      "items": [
                        { "name": null, "description": null, "quantity": null, "unitPrice": null, "discount": null, "taxRate": null, "taxAmount": null, "itemTotal": null }
                      ],
                      "financials": { "subtotal": null, "totalDiscount": null, "shippingCharges": null, "deliveryCharges": null, "serviceCharges": null, "handlingCharges": null, "platformFees": null, "otherCharges": null, "taxableAmount": null, "totalTax": null, "totalAmount": null, "amountPaid": null, "balanceDue": null },
                      "confidence": { "invoiceNumber": 0, "invoiceDate": 0, "sellerName": 0, "totalAmount": 0 }
                    }

                    ==================================================
                    18. FINAL VALIDATION BEFORE RESPONSE
                    ==================================================

                    Before returning the response, silently perform this checklist:

                    ✓ Did I analyze the entire document?
                    ✓ Did I correctly identify the document type?
                    ✓ Did I identify the actual vendor?
                    ✓ Did I identify the correct invoice number?
                    ✓ Did I identify the correct transaction date?
                    ✓ Did I correctly map item names, quantities and prices?
                    ✓ Did I distinguish unit price from line total?
                    ✓ Did I distinguish subtotal from final total?
                    ✓ Did I correctly identify GST/tax amounts?
                    ✓ Did I correctly handle discounts and charges?
                    ✓ Did I identify the explicitly labelled final payable amount?
                    ✓ Did I cross-check the financial calculations?
                    ✓ Did I avoid confusing unrelated numbers with financial fields?
                    ✓ Did I avoid guessing any value?
                    ✓ Did I check ALL pages before producing the final result?

                    If any answer is NO, re-analyze the OCR text before returning the JSON.

                    The final output must represent the actual financial document as accurately as possible.
                    """;

            return chatClient.prompt()
                    .system(systemInstruction)
                    .user(ocrText)
                    .call()
                    .entity(ReceiptAIResponse.class);

        } catch (Exception e) {
            log.error("AI parsing failed or timed out. Falling back to local OCR parser. Error: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public boolean isAiEnabled() {
        return this.isAiEnabled;
    }
}
