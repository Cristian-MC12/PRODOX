package com.mpdia.repository;

import com.mpdia.entity.Factor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FactorRepository extends JpaRepository<Factor, UUID> {
    List<Factor> findAllByOrderByNameAsc();
}
