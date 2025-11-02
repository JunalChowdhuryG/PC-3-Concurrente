const amqp = require('amqplib');
const mysql = require('mysql2/promise');

// --- Configuración de Conexión ---
const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672';
const MYSQL_CONFIG = {
    host: process.env.MYSQL_HOST || 'localhost',
    user: process.env.MYSQL_USER || 'shibasito_user',
    password: process.env.MYSQL_PASSWORD || 'shibasito_password',
    database: process.env.MYSQL_DATABASE || 'bd2_reniec'
};

const EXCHANGE_NAME = 'exchange_principal';
const QUEUE_NAME = 'q_reniec';
const ROUTING_KEY_VALIDAR = 'reniec.validar';

let dbConnectionPool;

// --- Función Principal ---
async function startService() {
    console.log('Iniciando servicio RENIEC (LP2)...');

    try {
        // 1. Conectar y PROBAR la Base de Datos MySQL
        // (Dejamos la prueba de conexión para asegurarnos de que el servicio esté listo)
        dbConnectionPool = mysql.createPool(MYSQL_CONFIG);
        console.log('Probando conexión a MySQL...');
        const connection = await dbConnectionPool.getConnection();
        await connection.query('SELECT 1');
        connection.release();
        console.log('Conectado y VERIFICADO a MySQL (db-reniec) exitosamente.');

        // 2. Conectar a RabbitMQ
        const rabbitConnection = await amqp.connect(RABBITMQ_URL);
        const channel = await rabbitConnection.createChannel();
        console.log('Conectado a RabbitMQ (rabbit-server) exitosamente.');

        // 3. Declarar Exchange, Cola y Binding
        await channel.assertExchange(EXCHANGE_NAME, 'direct', { durable: true });
        await channel.assertQueue(QUEUE_NAME, { durable: true });
        await channel.bindQueue(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY_VALIDAR);

        console.log(`[*] Esperando mensajes en la cola '${QUEUE_NAME}' con binding '${ROUTING_KEY_VALIDAR}'.`);
        
        channel.prefetch(1); 
        
        // 4. Empezar a consumir mensajes
        channel.consume(QUEUE_NAME, async (msg) => {
            if (msg.content) {
                const payload = JSON.parse(msg.content.toString());
                const dni = payload.dni;
                console.log(`[.] Mensaje recibido (clave: ${msg.fields.routingKey}). Validando DNI: ${dni}`);

                let response;

                // --- ¡¡INICIO DE LA SIMULACIÓN!! ---
                // Hemos comentado la consulta a la base de datos
                console.log(`[SIM] Simulación de consulta a BD iniciada...`);
                try {
                    // // const [rows] = await dbConnectionPool.execute(
                    // //     'SELECT * FROM Personas WHERE DNI = ?',
                    // //     [dni]
                    // // );
                    
                    // Simular un retraso de 1 segundo (como si la BD estuviera pensando)
                    await new Promise(resolve => setTimeout(resolve, 1000));
                    
                    // Crear una respuesta falsa
                    const fakeData = {
                        DNI: dni,
                        nombres: "Respuesta Falsa (Simulada)",
                        apell_pat: "PRUEBA",
                        apell_mat: "EXITOSA"
                    };
                    
                    console.log(`[SIM] Consulta simulada completada.`);
                    response = { status: 'OK', data: fakeData };

                } catch (dbError) {
                    console.error('Error en consulta a BD (simulada):', dbError.message);
                    response = { status: 'ERROR', message: 'Error interno del servidor' };
                }
                // --- ¡¡FIN DE LA SIMULACIÓN!! ---


                // 5. Responder al cliente (RPC)
                if (msg.properties.replyTo && msg.properties.correlationId) {
                    channel.sendToQueue(
                        msg.properties.replyTo,
                        Buffer.from(JSON.stringify(response)),
                        { correlationId: msg.properties.correlationId }
                    );
                    console.log(`[x] Respuesta (simulada) enviada para DNI: ${dni}`);
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