package com.chowdhury.shibasitoapp

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Usar 'use' en Kotlin es como 'try-with-resources' en Java
class RpcClient(hostIp: String) : AutoCloseable {

    private val connection = try {
        val factory = ConnectionFactory()
        factory.host = hostIp
        factory.username = "guest"
        factory.password = "guest"
        factory.newConnection()
    } catch (e: Exception) {
        throw IOException("No se pudo conectar a RabbitMQ en $hostIp: ${e.message}", e)
    }

    private val channel = connection.createChannel()
    private val replyQueueName: String

    init {
        // Crear cola de respuesta que NO se auto-borre
        replyQueueName = channel.queueDeclare("", false, true, false, null).queue
        println("Cliente RPC (Kotlin) conectado. Escuchando en: $replyQueueName")
    }

    @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
    fun call(routingKey: String, message: String): String {
        val corrId = UUID.randomUUID().toString()

        val props = AMQP.BasicProperties.Builder()
            .correlationId(corrId)
            .replyTo(replyQueueName)
            .build()

        val responseQueue = ArrayBlockingQueue<String>(1)

        val deliverCallback = DeliverCallback { _, delivery ->
            if (delivery.properties.correlationId == corrId) {
                responseQueue.offer(String(delivery.body, Charsets.UTF_8))
            }
        }

        val ctag = channel.basicConsume(replyQueueName, true, deliverCallback, { _ -> })

        channel.basicPublish("exchange_principal", routingKey, props, message.toByteArray(Charsets.UTF_8))

        // Esperar la respuesta (con timeout de 10s)
        val response = responseQueue.poll(10, TimeUnit.SECONDS)

        channel.basicCancel(ctag)

        return response ?: throw TimeoutException("RPC Timeout (10s)")
    }

    override fun close() {
        connection.close()
    }
}