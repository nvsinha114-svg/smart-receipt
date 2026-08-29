package com.smartreceipt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void dropOldOtpIndex() {
        if (mongoTemplate != null) {
            try {
                if (mongoTemplate.indexOps("otp_verifications").getIndexInfo().stream()
                        .anyMatch(index -> "expire_at_index".equals(index.getName()))) {
                    mongoTemplate.indexOps("otp_verifications").dropIndex("expire_at_index");
                    log.info("Successfully dropped old index 'expire_at_index' from 'otp_verifications' collection.");
                } else {
                    log.info("Index 'expire_at_index' does not exist on 'otp_verifications' collection, no need to drop.");
                }
            } catch (Exception e) {
                log.warn("Could not drop old index 'expire_at_index' from 'otp_verifications' collection: {}", e.getMessage());
            }
        }
    }
}
