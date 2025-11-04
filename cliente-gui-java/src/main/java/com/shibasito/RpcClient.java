package com.shibasito;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RpcClient implements AutoCloseable {

    private final Connection connection;
    private final Channel channel;
    private final String replyQueueName;

    public RpcClient(String hostIp) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostIp);
        // Usamos 'guest' (permitido gracias a loopback_users = none)
        factory.setUsername("guest");
        factory.setPassword("guest");

        // Conectarse a RabbitMQ (que está en tu PC)
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Crear una cola de respuesta temporal, exclusiva y que NO se auto-borre
        replyQueueName = channel.queueDeclare("", false, true, false, null).getQueue();
        System.out.println("Cliente RPC conectado. Escuchando respuestas en: " + replyQueueName);
    }

    public String call(String routingKey, String message) throws IOException, InterruptedException {
        final String corrId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        final BlockingQueue<String> responseQueue = new ArrayBlockingQueue<>(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                responseQueue.offer(new String(delivery.getBody(), "UTF-8"));
            }
        };

        // Empezar a consumir de la cola de respuesta
        String ctag = channel.basicConsume(replyQueueName, true, deliverCallback, consumerTag -> {});

        // Publicar la petición
        System.out.println("-> [Hilo] Enviando Petición: " + routingKey);
        channel.basicPublish("exchange_principal", routingKey, props, message.getBytes("UTF-8"));

        // Esperar la respuesta (con un timeout de 10 segundos)
        String response = responseQueue.poll(10, java.util.concurrent.TimeUnit.SECONDS);

        // Dejar de consumir de la cola
        channel.basicCancel(ctag);

        if (response == null) {
            System.out.println("<- [Hilo] Respuesta Recibida: TIMEOUT");
            return "{\"status\":\"ERROR\", \"message\":\"Tiempo de espera agotado (Timeout)\"}";
        }

        System.out.println("<- [Hilo] Respuesta Recibida.");
        return response;
    }

    @Override
    public void close() throws IOException {
        // Al cerrar la conexión, la cola (marcada como 'exclusive') se borrará.
        connection.close();
    }
}