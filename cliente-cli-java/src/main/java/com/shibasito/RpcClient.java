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
        // Volvemos a 'guest', ya que 'rabbitmq.conf' ahora lo permite
        factory.setUsername("shibasito_user");
        factory.setPassword("shibasito_password");

        connection = factory.newConnection();
        channel = connection.createChannel();

        // --- CORRECCIÓN CLAVE ---
        // Declarar una cola que NO se auto-borre (autoDelete: false)
        // pero SÍ sea exclusiva (exclusive: true)
        replyQueueName = channel.queueDeclare("", false, true, false, null).getQueue();
        // --- FIN DE LA CORRECCIÓN ---

        System.out.println("Cliente RPC conectado. Escuchando respuestas en: " + replyQueueName);
    }

    public String call(String routingKey, String message) throws IOException, InterruptedException {
        final String corrId = UUID.randomUUID().toString();
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId).replyTo(replyQueueName).build();

        final BlockingQueue<String> responseQueue = new ArrayBlockingQueue<>(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                responseQueue.offer(new String(delivery.getBody(), "UTF-8"));
            }
        };

        // Suscribirse a la cola de respuesta
        String ctag = channel.basicConsume(replyQueueName, true, deliverCallback, consumerTag -> {});

        // Publicar la petición
        channel.basicPublish("exchange_principal", routingKey, props, message.getBytes("UTF-8"));

        // Esperar la respuesta (con timeout)
        String response = responseQueue.poll(10, java.util.concurrent.TimeUnit.SECONDS);

        // Cancelar la suscripción
        channel.basicCancel(ctag);

        if (response == null) {
            throw new InterruptedException("RPC Timeout (10s)");
        }
        return response;
    }

    @Override
    public void close() throws IOException {
        // Al cerrar la conexión, la cola (marcada como 'exclusive') se borrará.
        connection.close();
    }
}