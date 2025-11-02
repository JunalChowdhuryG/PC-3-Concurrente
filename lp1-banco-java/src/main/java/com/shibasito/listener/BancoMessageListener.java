package com.shibasito.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

// Importar la configuración para usar las constantes
import com.shibasito.config.RabbitMQConfig;

@Component
public class BancoMessageListener {

    private static final Logger log = LoggerFactory.getLogger(BancoMessageListener.class);

    @Autowired
    private JdbcTemplate jdbcTemplate; // Para interactuar con la BD

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Este método escucha la cola 'q_banco'.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_BANCO)
    public String handleMessage(String payload, Message message) {

        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.info("[.] Mensaje recibido en LP1 (Java) con routingKey: {}", routingKey);

        try {
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);

            // Decidimos qué hacer basándonos en el routing key
            switch (routingKey) {
                case "banco.consulta.saldo":
                    return consultarSaldo(request);

                case "banco.prestamo.solicitar":
                    return solicitarPrestamo(request);

                default:
                    log.warn("RoutingKey no reconocido: {}", routingKey);
                    return createErrorResponse("Operación no reconocida: " + routingKey);
            }

        } catch (Exception e) {
            log.error("Error procesando mensaje: {}", e.getMessage());
            return createErrorResponse("Error interno en el servidor: " + e.getMessage());
        }
    }

    // --- Lógica de Negocio (Ejemplos) ---

    private String consultarSaldo(Map<String, Object> request) {
        try {
            String idCliente = (String) request.get("idCliente");
            String sql = "SELECT saldo FROM Cuentas WHERE id_cliente = ?";

            Double saldo = jdbcTemplate.queryForObject(sql, Double.class, idCliente);
            return createSuccessResponse(Map.of("idCliente", idCliente, "saldo", saldo));

        } catch (Exception e) {
            return createErrorResponse("No se pudo consultar el saldo: " + e.getMessage());
        }
    }

    private String solicitarPrestamo(Map<String, Object> request) {
        try {
            String idCliente = (String) request.get("idCliente");
            Double monto = ((Number) request.get("monto")).doubleValue();
            String idPrestamo = "PR" + (int)(Math.random() * 1000);

            String sql = "INSERT INTO Prestamos (id_prestamo, id_cliente, monto_total, monto_pendiente, estado, fecha_solicitud) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, idPrestamo, idCliente, monto, monto, "activo", new java.util.Date());
            return createSuccessResponse(Map.of("idPrestamo", idPrestamo, "estado", "aprobado"));

        } catch (Exception e) {
            return createErrorResponse("No se pudo registrar el préstamo: " + e.getMessage());
        }
    }


    // --- Métodos Utilitarios para crear respuestas JSON ---

    private String createErrorResponse(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "ERROR", "message", message));
        } catch (Exception e) {
            return "{\"status\":\"ERROR\", \"message\":\"Error de formato en respuesta\"}";
        }
    }

    private String createSuccessResponse(Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "OK", "data", data));
        } catch (Exception e) {
            return createErrorResponse("Error al formatear la respuesta: " + e.getMessage());
        }
    }
}