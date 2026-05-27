// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.MetricUsoRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MetricUsoRankingRepository extends JpaRepository<MetricUsoRanking, UUID> {

    /** Top N métricas más usadas */
    List<MetricUsoRanking> findTop5ByOrderByUsosDesc();
}
