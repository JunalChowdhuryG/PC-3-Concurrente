package com.shibasito.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "exchange_principal";
    public static final String QUEUE_BANCO = "q_banco";
    
    // --- CAMBIO: Claves de binding exactas ---
    public static final String KEY_SALDO = "banco.consulta.saldo";
    public static final String KEY_PRESTAMO = "banco.prestamo.solicitar";

    /**
     * Define el Exchange principal (tipo Directo).
     */
    @Bean
    DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    /**
     * Define la cola para el servicio de Banco ('q_banco').
     */
    @Bean
    Queue bancoQueue() {
        return new Queue(QUEUE_BANCO, true);
    }

    /**
     * Define los Bindings (uniones) entre el Exchange y la Cola.
     * Ahora creamos un binding por CADA clave que queremos escuchar.
     */
    @Bean
    Binding bancoSaldoBinding(Queue bancoQueue, DirectExchange exchange) {
        return BindingBuilder.bind(bancoQueue).to(exchange).with(KEY_SALDO);
    }
    
    @Bean
    Binding bancoPrestamoBinding(Queue bancoQueue, DirectExchange exchange) {
        return BindingBuilder.bind(bancoQueue).to(exchange).with(KEY_PRESTAMO);
    }
}