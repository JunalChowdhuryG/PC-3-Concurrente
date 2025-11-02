const amqp = require('amqplib');
const mysql = require('mysql2/promise');

// Leemos la URL completa desde la variable de entorno
const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672';const MYSQL_CONFIG = {
    host: process.env.MYSQL_HOST,
    user: process.env.MYSQL_USER,
    password: process.env.MYSQL_PASSWORD,
    database: process.env.MYSQL_DATABASE
};

const EXCHANGE_NAME = 'exchange_principal';
const QUEUE_NAME = 'q_reniec';
const ROUTING_KEY_VALIDAR = 'reniec.validar';

let dbConnectionPool;

async function startService() {
    console.log('Iniciando servicio RENIEC (LP2)...');
    try {
        dbConnectionPool = mysql.createPool(MYSQL_CONFIG);
        console.log('Probando conexión a MySQL...');
        const connection = await dbConnectionPool.getConnection();
        await connection.query('SELECT 1');
        connection.release();
        console.log('Conectado y VERIFICADO a MySQL (db-reniec) exitosamente.');

        const rabbitConnection = await amqp.connect(RABBITMQ_URL); // Usa la URL completa
        const channel = await rabbitConnection.createChannel();
        console.log('Conectado a RabbitMQ (rabbit-server) exitosamente.');

        await channel.assertExchange(EXCHANGE_NAME, 'direct', { durable: true });
        await channel.assertQueue(QUEUE_NAME, { durable: true });
        await channel.bindQueue(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY_VALIDAR);

        console.log(`[*] Esperando mensajes en la cola '${QUEUE_NAME}' con binding '${ROUTING_KEY_VALIDAR}'.`);
        
        channel.prefetch(1); 
        channel.consume(QUEUE_NAME, async (msg) => {
            if (msg.content) {
                const payload = JSON.parse(msg.content.toString());
                const dni = payload.dni;
                console.log(`[.] Mensaje recibido (clave: ${msg.fields.routingKey}). Validando DNI: ${dni}`);

                let response;
                try {
                    console.log(`[DB] Iniciando consulta para DNI: ${dni}`);
                    const [rows] = await dbConnectionPool.execute(
                        'SELECT * FROM Personas WHERE DNI = ?',
                        [dni]
                    );
                    console.log(`[DB] Consulta completada. Filas encontradas: ${rows.length}`);

                    if (rows.length > 0) {
                        response = { status: 'OK', data: rows[0] };
                    } else {
                        response = { status: 'ERROR', message: 'DNI no encontrado' };
                    }
                } catch (dbError) {
                    console.error('Error en consulta a BD:', dbError.message);
                    response = { status: 'ERROR', message: 'Error interno del servidor' };
                }

                if (msg.properties.replyTo && msg.properties.correlationId) {
                    channel.sendToQueue(
                        msg.properties.replyTo,
                        Buffer.from(JSON.stringify(response)),
                        { correlationId: msg.properties.correlationId }
                    );
                    console.log(`[x] Respuesta enviada para DNI: ${dni}`);
                }
                
                channel.ack(msg);
            }
        });

    } catch (error) {
        console.error('Error al iniciar el servicio (FALLO EN BD o RABBITMQ):', error.message);
        console.log('Reintentando en 5 segundos...');
        setTimeout(startService, 5000);
    }
}
startService();