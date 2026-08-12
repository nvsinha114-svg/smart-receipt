package com.smartreceipt.repository;

import com.smartreceipt.entity.Receipt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends MongoRepository<Receipt, String> {

    List<Receipt> findByUserId(String userId);

    Optional<Receipt> findByIdAndUserId(String id, String userId);
}
