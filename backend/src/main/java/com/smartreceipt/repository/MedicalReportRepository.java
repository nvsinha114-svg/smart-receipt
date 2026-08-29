package com.smartreceipt.repository;

import com.smartreceipt.entity.MedicalReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalReportRepository extends MongoRepository<MedicalReport, String> {
    List<MedicalReport> findByUserId(String userId);
    Optional<MedicalReport> findByIdAndUserId(String id, String userId);
}
