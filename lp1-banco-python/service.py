import pika
import os
import time
import json
import psycopg2
from psycopg2 import pool
from psycopg2.extras import RealDictCursor
from decimal import Decimal

# --- CONFIGURACIÓN ---
RABBIT_URL = os.getenv('RABBITMQ_URL', 'amqp://guest:guest@host.docker.internal:5672')
DB_HOST = os.getenv('DB_HOST', 'db-banco')
DB_NAME = os.getenv('DB_NAME', 'bd1_banco')
DB_USER = os.getenv('DB_USER', 'shibasito_user')
DB_PASS = os.getenv('DB_PASS', 'shibasito_password')

EXCHANGE_NAME = 'exchange_principal'
QUEUE_NAME = 'q_banco'
KEY_SALDO = "banco.consulta.saldo"
KEY_HISTORIAL = "banco.historial"
KEY_SOLICITAR_PRESTAMO = "banco.prestamo.solicitar"

db_pool = None

def get_db_connection():
    return db_pool.getconn()

def release_db_connection(conn):
    db_pool.putconn(conn)

def consultar_saldo(request):
    conn = None
    try:
        id_cliente = request.get('idCliente')
        conn = get_db_connection()
        cursor = conn.cursor()
        print(f"[DB] Iniciando consulta de saldo para: {id_cliente}")
        sql = "SELECT saldo FROM Cuentas WHERE id_cliente = %s"
        cursor.execute(sql, (id_cliente,))
        result = cursor.fetchone() # Esto es un dict: {'saldo': Decimal('2500.00')}
        cursor.close()
        
        if result:
            print(f"[DB] Consulta completada. Saldo: {result['saldo']}")
            
            # --- ¡AQUÍ ESTÁ LA CORRECCIÓN! ---
            # Convertir el Decimal a float antes de devolverlo
            result['saldo'] = float(result['saldo'])
            # --- FIN DE LA CORRECCIÓN ---

            result['idCliente'] = id_cliente
            return {"status": "OK", "data": result}
        else:
            print("[DB] Cliente no encontrado.")
            return {"status": "ERROR", "message": "Cliente no encontrado"}
    except Exception as e:
        print(f"[DB Error] {e}")
        return {"status": "ERROR", "message": str(e)}
    finally:
        if conn: release_db_connection(conn)

def consultar_historial(request):
    conn = None
    try:
        id_cliente = request.get('idCliente')
        conn = get_db_connection()
        cursor = conn.cursor()
        print(f"[DB] Consultando historial para: {id_cliente}")
        sql = """
            SELECT T.fecha, T.tipo, T.monto 
            FROM Transacciones T
            JOIN Cuentas C ON T.id_cuenta = C.id_cuenta
            WHERE C.id_cliente = %s
            ORDER BY T.fecha DESC
            LIMIT 10
        """
        cursor.execute(sql, (id_cliente,))
        historial = cursor.fetchall()
        cursor.close()
        for fila in historial:
            fila['monto'] = str(fila['monto']) # Convertir Decimal a str
            fila['fecha'] = fila['fecha'].isoformat() # Convertir Date a str
        print(f"[DB] Historial encontrado. Filas: {len(historial)}")
        return {"status": "OK", "data": historial}
    except Exception as e:
        print(f"[DB Error] {e}")
        return {"status": "ERROR", "message": str(e)}
    finally:
        if conn: release_db_connection(conn)

def solicitar_prestamo(request):
    conn = None
    try:
        id_cliente = request.get('idCliente')
        monto = Decimal(str(request.get('monto')))
        id_prestamo = f"PR{int(time.time()) % 100000000:08d}"
        
        if monto <= 0:
            return {"status": "ERROR", "message": "El monto debe ser positivo"}
        conn = get_db_connection()
        cursor = conn.cursor()
        print(f"[DB] Registrando prestamo {id_prestamo} de {monto} para {id_cliente}")
        sql = """
            INSERT INTO Prestamos (id_prestamo, id_cliente, monto_total, monto_pendiente, estado, fecha_solicitud)
            VALUES (%s, %s, %s, %s, %s, NOW())
        """
        cursor.execute(sql, (id_prestamo, id_cliente, monto, monto, 'activo'))
        sql_deposito = "UPDATE Cuentas SET saldo = saldo + %s WHERE id_cliente = %s"
        cursor.execute(sql_deposito, (monto, id_cliente))
        conn.commit()
        cursor.close()
        print("[DB] Prestamo registrado y depositado.")
        return {"status": "OK", "data": {"idPrestamo": id_prestamo, "estado": "aprobado", "mensaje": "Prestamo aprobado y depositado."}}
    except Exception as e:
        if conn: conn.rollback()
        print(f"[DB Error] {e}")
        return {"status": "ERROR", "message": "Error al solicitar prestamo: " + str(e)}
    finally:
        if conn: release_db_connection(conn)

def on_message_received(ch, method, properties, body):
    routing_key = method.routing_key
    print(f"\n[.] Mensaje recibido (clave: {routing_key})")
    try:
        request = json.loads(body.decode('utf-8'))
        response = None
        if routing_key == KEY_SALDO:
            response = consultar_saldo(request)
        elif routing_key == KEY_HISTORIAL:
            response = consultar_historial(request)
        elif routing_key == KEY_SOLICITAR_PRESTAMO:
            response = solicitar_prestamo(request)
        else:
            response = {"status": "ERROR", "message": "RoutingKey no reconocido"}
        
        if properties.reply_to:
            ch.basic_publish(exchange='', routing_key=properties.reply_to,
                             properties=pika.BasicProperties(correlation_id=properties.correlation_id),
                             body=json.dumps(response)) # Esto ahora funcionará
            print(f"[x] Respuesta enviada a {properties.reply_to}")
    except Exception as e:
        print(f"[Error] Procesando mensaje: {e}") # Aquí es donde fallaba
    ch.basic_ack(delivery_tag=method.delivery_tag)

def start_service():
    global db_pool
    print("Iniciando servicio Banco (LP1 - Python)...")
    
    while db_pool is None:
        try:
            print(f"Probando conexión a PostgreSQL ({DB_HOST})...")
            db_pool = psycopg2.pool.SimpleConnectionPool(1, 5, host=DB_HOST, database=DB_NAME, user=DB_USER, password=DB_PASS, cursor_factory=RealDictCursor)
            conn = get_db_connection()
            conn.cursor().execute('SELECT 1')
            release_db_connection(conn)
            print("Conectado y VERIFICADO a PostgreSQL (db-banco) exitosamente.")
        except Exception as e:
            print(f"No se pudo conectar a la BD: {e}. Reintentando en 5s...")
            time.sleep(5)
            
    while True:
        try:
            print(f"Conectando a RabbitMQ en {RABBIT_URL}...")
            connection = pika.BlockingConnection(pika.URLParameters(RABBIT_URL))
            channel = connection.channel()
            print("Conectado a RabbitMQ (en Host) exitosamente.")

            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type='direct', durable=True)
            channel.queue_declare(queue=QUEUE_NAME, durable=True)
            channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=KEY_SALDO)
            channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=KEY_HISTORIAL)
            channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=KEY_SOLICITAR_PRESTAMO)
            
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