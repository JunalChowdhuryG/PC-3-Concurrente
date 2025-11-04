import pika
import os
import time
import json
import psycopg2
from psycopg2 import pool

# --- CONFIGURACIÓN (leída desde Docker-Compose) ---
RABBIT_URL = os.getenv('RABBITMQ_URL', 'amqp://shibasito_user:shibasito_password@localhost:5672')
DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_NAME = os.getenv('DB_NAME', 'bd1_banco')
DB_USER = os.getenv('DB_USER', 'shibasito_user')
DB_PASS = os.getenv('DB_PASS', 'shibasito_password')

EXCHANGE_NAME = 'exchange_principal'
QUEUE_NAME = 'q_banco'
KEY_SALDO = "banco.consulta.saldo"
KEY_PRESTAMO = "banco.prestamo.solicitar"

db_pool = None

def get_db_connection():
    return db_pool.getconn()

def release_db_connection(conn):
    db_pool.putconn(conn)

def consultar_saldo(request):
    """Lógica para consultar el saldo."""
    conn = None
    try:
        id_cliente = request.get('idCliente')
        conn = get_db_connection()
        cursor = conn.cursor()
        
        print(f"[DB] Iniciando consulta de saldo para: {id_cliente}")
        sql = "SELECT saldo FROM Cuentas WHERE id_cliente = %s"
        cursor.execute(sql, (id_cliente,))
        result = cursor.fetchone()
        cursor.close()
        
        if result:
            saldo = result[0]
            print(f"[DB] Consulta completada. Saldo: {saldo}")
            return {"status": "OK", "data": {"idCliente": id_cliente, "saldo": float(saldo)}}
        else:
            print("[DB] Cliente no encontrado.")
            return {"status": "ERROR", "message": "Cliente no encontrado"}
    
    except Exception as e:
        print(f"[DB Error] {e}")
        return {"status": "ERROR", "message": str(e)}
    finally:
        if conn:
            release_db_connection(conn)

# (Aquí puedes añadir la lógica para 'solicitar_prestamo' después)

# --- CALLBACK DE RABBITMQ ---
def on_message_received(ch, method, properties, body):
    """Procesa los mensajes recibidos."""
    routing_key = method.routing_key
    print(f"[.] Mensaje recibido (clave: {routing_key})")
    
    try:
        request = json.loads(body.decode('utf-8'))
        response = None

        if routing_key == KEY_SALDO:
            response = consultar_saldo(request)
        # elif routing_key == KEY_PRESTAMO:
        #     response = solicitar_prestamo(request)
        else:
            response = {"status": "ERROR", "message": "RoutingKey no reconocido"}
        
        # Enviar la respuesta
        if properties.reply_to:
            ch.basic_publish(
                exchange='',
                routing_key=properties.reply_to,
                properties=pika.BasicProperties(correlation_id=properties.correlation_id),
                body=json.dumps(response)
            )
            print(f"[x] Respuesta enviada a {properties.reply_to}")
            
    except Exception as e:
        print(f"[Error] Procesando mensaje: {e}")
        
    ch.basic_ack(delivery_tag=method.delivery_tag)

# --- FUNCIÓN PRINCIPAL ---
def start_service():
    global db_pool
    print("Iniciando servicio Banco (LP1 - Python)...")
    
    # 1. Conectar a la BD (con reintentos)
    while db_pool is None:
        try:
            print("Probando conexión a PostgreSQL (db-banco)...")
            db_pool = psycopg2.pool.SimpleConnectionPool(1, 5, host=DB_HOST, database=DB_NAME, user=DB_USER, password=DB_PASS)
            conn = get_db_connection()
            conn.cursor().execute('SELECT 1')
            release_db_connection(conn)
            print("Conectado y VERIFICADO a PostgreSQL (db-banco) exitosamente.")
        except Exception as e:
            print(f"No se pudo conectar a la BD: {e}. Reintentando en 5s...")
            time.sleep(5)
            
    # 2. Conectar a RabbitMQ (con reintentos)
    while True:
        try:
            print(f"Conectando a RabbitMQ en {RABBIT_URL}...")
            connection = pika.BlockingConnection(pika.URLParameters(RABBIT_URL))
            channel = connection.channel()
            print("Conectado a RabbitMQ (rabbit-server) exitosamente.")

            # 3. Declarar Exchange, Cola y Bindings
            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type='direct', durable=True)
            channel.queue_declare(queue=QUEUE_NAME, durable=True)
            channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=KEY_SALDO)
            # channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=KEY_PRESTAMO)
            
            print(f"[*] Esperando mensajes en la cola '{QUEUE_NAME}'.")
            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=on_message_received)
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            print(f"No se pudo conectar a RabbitMQ: {e}. Reintentando en 5s...")
            time.sleep(5)
        except Exception as e:
            print(f"Error inesperado: {e}. Reiniciando consumidor...")
            time.sleep(5)

if __name__ == "__main__":
    start_service()