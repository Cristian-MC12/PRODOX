// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.util;

import com.prodox.entity.MetricParametrizacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Utilidad para calcular fingerprints SHA-256 de parametrizaciones.
 *
 * Corrección de auditoría (ranking de parametrizaciones): el conjunto de campos
 * sustantivos usado acá DEBE ser exactamente el mismo que ya usa
 * MetricRankingService.esMismoContenido() (el criterio de "mismo contenido" ya
 * auditado y en producción para detectar reenvíos duplicados dentro de un mismo
 * proyecto+métrica) — evita tener DOS definiciones distintas de "misma
 * parametrización" conviviendo en el mismo código base. Se agregó
 * fuenteAcademica (faltaba en la versión original de este archivo) para que
 * coincida exactamente con esMismoContenido(). Campos deliberadamente
 * EXCLUIDOS por ser metadatos, no parte de la definición: id, version, factor,
 * userId/userEmail (autor), proyectoId, status, revisadoPor/revisadoAt,
 * motivoRechazo, metricaBaseId, propuestaIAJson, configuracionAprobadaJson
 * (snapshot derivado, no fuente), createdAt, escalaDescripcion (texto libre
 * descriptivo, ya excluido también por esMismoContenido()).
 */
public class FingerprintUtil {

    public static String calcularFingerprint(MetricParametrizacion p) {
        if (p == null) {
            throw new IllegalArgumentException("Parametrizacion no puede ser null");
        }

        StringBuilder sb = new StringBuilder();

        sb.append(normalizeUUID(p.getMetricaId())).append("|");
        sb.append(normalizeText(p.getObjetivo())).append("|");
        sb.append(normalizeText(p.getProcedimiento())).append("|");
        sb.append(normalizeText(p.getIndicadorVariable())).append("|");
        sb.append(normalizeText(p.getEscala())).append("|");
        sb.append(normalizeTechnical(p.getEscalaTipo())).append("|");
        sb.append(normalizeNumber(p.getEscalaMin())).append("|");
        sb.append(normalizeNumber(p.getEscalaMax())).append("|");
        sb.append(normalizeNumber(p.getEscalaPaso())).append("|");
        sb.append(normalizeBoolean(p.getEscalaSinLimite())).append("|");
        sb.append(normalizeTechnical(p.getTipoOperacion())).append("|");
        sb.append(normalizeText(p.getFormulaAcademica())).append("|");
        sb.append(normalizeText(p.getUnidadResultado())).append("|");
        sb.append(normalizeText(p.getFuenteAcademica())).append("|");
        sb.append(normalizeText(p.getFrecuenciaCaptura() != null ? p.getFrecuenciaCaptura() : "por_sprint")).append("|");
        sb.append(normalizeTechnical(p.getResponsableCaptura() != null ? p.getResponsableCaptura() : "SCRUM_MASTER"));

        return sha256Hex(sb.toString());
    }

    private static String normalizeUUID(UUID uuid) {
        if (uuid == null) return "";
        return uuid.toString().replace("-", "").toLowerCase();
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) return "";
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String normalizeTechnical(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().toUpperCase();
    }

    private static String normalizeNumber(BigDecimal number) {
        if (number == null) return "";
        return number.stripTrailingZeros().toPlainString();
    }

    private static String normalizeBoolean(Boolean value) {
        return String.valueOf(value != null ? value : false);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}