package com.smartreceipt.config;

import com.google.auth.Credentials;
import com.google.cloud.vertexai.VertexAI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class GeminiConfig {

    @Value("${spring.ai.vertex.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${spring.ai.vertex.ai.gemini.project-id:smart-receipt}")
    private String projectId;

    @Value("${spring.ai.vertex.ai.gemini.location:us-central1}")
    private String location;

    @Bean
    public VertexAI vertexAi() {
        VertexAI.Builder builder = new VertexAI.Builder();
        
        if (projectId != null && !projectId.trim().isEmpty() && !projectId.contains("GEMINI_PROJECT_ID")) {
            builder.setProjectId(projectId);
        }
        if (location != null && !location.trim().isEmpty() && !location.contains("GEMINI_LOCATION")) {
            builder.setLocation(location);
        }

        if (apiKey != null && !apiKey.trim().isEmpty() 
                && !apiKey.equals("dummy-key-to-bypass-startup-check")
                && !apiKey.contains("GEMINI_API_KEY")) {
            
            log.info("Configuring VertexAI with custom credentials to use API Key.");
            
            Credentials credentials = new Credentials() {
                @Override
                public String getAuthenticationType() {
                    return "API_KEY";
                }

                @Override
                public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
                    return Collections.singletonMap("x-goog-api-key", Collections.singletonList(apiKey));
                }

                @Override
                public boolean hasRequestMetadata() {
                    return true;
                }

                @Override
                public boolean hasRequestMetadataOnly() {
                    return true;
                }

                @Override
                public void refresh() throws IOException {
                    // No-op
                }
            };
            builder.setCredentials(credentials);
        } else {
            log.info("VertexAI initialized without custom API key credentials (local or test environment).");
        }
        
        return builder.build();
    }
}
