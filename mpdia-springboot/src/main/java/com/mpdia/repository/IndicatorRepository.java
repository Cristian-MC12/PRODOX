package com.mpdia.repository;

import com.mpdia.entity.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IndicatorRepository extends JpaRepository<Indicator, UUID> {
    List<Indicator> findAllByOrderByMeasuredAtDesc();
}
