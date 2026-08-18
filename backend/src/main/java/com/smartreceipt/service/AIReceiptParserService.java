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
    public AIReceiptParserService(ChatModel chatModel, @Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty() 
                && !apiKey.equals("dummy-key-to-bypass-startup-check") 
                && !apiKey.contains("OPENAI_API_KEY")) {
            this.chatClient = ChatClient.create(chatModel);
            this.isAiEnabled = true;
            log.info("Spring AI integration initialized successfully with OpenAI ChatModel.");
        } else {
            this.chatClient = null;
            this.isAiEnabled = false;
            log.warn("Spring AI API Key is not configured. AI parsing will be bypassed, falling back to Tesseract OCR parser.");
        }
    }

    public ReceiptAIResponse parseReceiptText(String ocrText) {
        if (!isAiEnabled || ocrText == null || ocrText.trim().isEmpty()) {
            return null;
        }

        try {
            log.info("Sending raw OCR text to LLM for structured parsing...");
            String systemInstruction = """
                    You are an expert AI receipt parser. Analyze the following noisy OCR text from a receipt and extract structured data.
                    Ensure you follow these rules:
                    1. Extract merchantName, receiptDate (in YYYY-MM-DD format if possible), currency, category, and a list of items.
                    2. If currency is represented by ₹, Rs, Rs., INR, or is an Indian receipt, set the currency to 'INR'.
                    3. For each item, extract name, quantity, and unitPrice.
                    4. Clean item names. Handle spelling mistakes from OCR and preserve names containing special characters like colons, commas, slashes, ampersands, and parentheses.
                    5. Ignore metadata such as receipt IDs, semester numbers, page numbers, timestamps, and other unrelated numbers.
                    6. Do not invent missing values; use null if a field cannot be confidently extracted.
                    7. Under no circumstances should you calculate or modify totals. Extract only raw item quantities and unit prices.
                    """;

            return chatClient.prompt()
                    .system(systemInstruction)
                    .user(ocrText)
                    .call()
                    .entity(ReceiptAIResponse.class);

        } catch (Exception e) {
            log.error("AI parsing failed or timed out. Falling back to local OCR parsing logic. Error: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public boolean isAiEnabled() {
        return this.isAiEnabled;
    }
}
