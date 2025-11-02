import tkinter as tk
from tkinter import messagebox, scrolledtext
import pika
import uuid
import json
import os
import threading
import time 

# --- CONFIGURACIÓN ---
RABBITMQ_USER = 'guest'
RABBITMQ_PASS = 'guest'
TU_IP_WIFI = '10.237.90.216' # ¡Esta es la IP de tu Wi-Fi que encontraste!

# --- Lógica del Cliente RPC para RabbitMQ ---
class RpcClient:
    def __init__(self, host):
        self.host = host
        self.connection = None
        self.channel = None
        self.callback_queue = None
        self.response = None
        self.corr_id = None
        
        try:
            # Añadir credenciales a la conexión
            self.credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
            self.connection = pika.BlockingConnection(
                pika.ConnectionParameters(
                    host=self.host,
                    credentials=self.credentials,
                    connection_attempts=5, 
                    retry_delay=3
                )
            )
            self.channel = self.connection.channel()
            result = self.channel.queue_declare(queue='', exclusive=True)
            self.callback_queue = result.method.queue
            self.channel.basic_consume(
                queue=self.callback_queue,
                on_message_callback=self.on_response,
                auto_ack=True)
            print("RPC Client conectado y cola de respuesta lista.")
        except Exception as e:
            print(f"Error fatal en RPCClient.connect: {e}")
            raise

    # ... (El resto de la clase RpcClient: close, on_response, call... no cambian) ...
    def close(self):
        try:
            if self.connection and self.connection.is_open:
                self.connection.close()
                print("RPC Client desconectado.")
        except Exception as e:
            print(f"Error al cerrar conexión RPC: {e}")

    def on_response(self, ch, method, props, body):
        if self.corr_id == props.correlation_id:
            self.response = body

    def call(self, routing_key, message_body):
        self.response = None
        self.corr_id = str(uuid.uuid4())
        
        try:
            self.channel.basic_publish(
                exchange='exchange_principal',
                routing_key=routing_key,
                properties=pika.BasicProperties(
                    reply_to=self.callback_queue,
                    correlation_id=self.corr_id,
                ),
                body=json.dumps(message_body))
            
            print(f"Publicado {self.corr_id}. Esperando respuesta...")

            timeout = 5 
            while self.response is None and timeout > 0:
                self.connection.process_data_events(time_limit=0.1)
                timeout -= 0.1

            if self.response is None:
                return {"status": "ERROR", "message": "Tiempo de espera agotado (Timeout)"}
            
            return json.loads(self.response.decode('utf-8'))
        
        except Exception as e:
            print(f"Error en RPC call: {e}")
            return {"status": "ERROR", "message": str(e)}

# --- Aplicación GUI con Tkinter ---
class App:
    def __init__(self, root):
        self.root = root
        self.root.title("Banco Shibasito - Cliente Desktop (Python)")
        self.root.geometry("600x400")
        
        self.log_area = scrolledtext.ScrolledText(root, state='disabled', height=10)
        self.log_area.pack(pady=10, padx=10, fill="x")

        # --- CAMBIO IMPORTANTE ---
        self.rabbit_host = TU_IP_WIFI 
        self.log(f"Host de RabbitMQ configurado en: {self.rabbit_host}")
        
        # ... (Frames de GUI - sin cambios) ...
        reniec_frame = tk.LabelFrame(root, text="Validación RENIEC (LP2)")
        reniec_frame.pack(fill="x", padx=10, pady=5)
        tk.Label(reniec_frame, text="DNI:").grid(row=0, column=0, padx=5, pady=5)
        self.reniec_dni = tk.Entry(reniec_frame)
        self.reniec_dni.insert(0, "12345678")
        self.reniec_dni.grid(row=0, column=1, padx=5, pady=5)
        self.reniec_button = tk.Button(reniec_frame, text="Validar Identidad", command=self.validar_reniec)
        self.reniec_button.grid(row=0, column=2, padx=5, pady=5)
        
        banco_frame = tk.LabelFrame(root, text="Operaciones Banco (LP1)")
        banco_frame.pack(fill="x", padx=10, pady=5)
        tk.Label(banco_frame, text="ID Cliente:").grid(row=0, column=0, padx=5, pady=5)
        self.banco_cliente_id = tk.Entry(banco_frame)
        self.banco_cliente_id.insert(0, "CL001")
        self.banco_cliente_id.grid(row=0, column=1, padx=5, pady=5)
        self.saldo_button = tk.Button(banco_frame, text="Consultar Saldo", command=self.consultar_saldo)
        self.saldo_button.grid(row=0, column=2, padx=5, pady=5)


    def log(self, message):
        self.log_area.config(state='normal')
        self.log_area.insert(tk.END, f"{message}\n")
        self.log_area.config(state='disabled')
        self.log_area.see(tk.END)

    def ejecutar_rpc(self, routing_key, body):
        def rpc_call():
            rpc_client = None
            try:
                # Retraso para prevenir la 'race condition'
                self.log(f"-> [Hilo] Esperando 5s a que el backend arranque...")
                time.sleep(5) 
                
                self.log(f"-> [Hilo] Creando cliente RPC para {self.rabbit_host}...")
                rpc_client = RpcClient(self.rabbit_host) 
                
                self.log(f"-> [Hilo] Enviando Petición: {routing_key} | Body: {body}")
                response = rpc_client.call(routing_key, body) 
                
                self.log(f"<- [Hilo] Respuesta Recibida: {response}")
                self.root.after(0, self.mostrar_respuesta, response)
            except Exception as e:
                self.log(f"Error en hilo RPC: {e}")
                self.root.after(0, self.mostrar_respuesta, 
                    {"status": "ERROR", "message": f"Error en hilo RPC: {e}"})
            finally:
                if rpc_client:
                    rpc_client.close()
            
        threading.Thread(target=rpc_call, daemon=True).start()

    def mostrar_respuesta(self, response):
        if response.get("status") == "OK":
            messagebox.showinfo("Respuesta Exitosa", json.dumps(response.get("data"), indent=2))
        else:
            messagebox.showerror("Error en Respuesta", response.get("message", "Error desconocido"))

    # ... (Handlers de botones - sin cambios) ...
    def validar_reniec(self):
        dni = self.reniec_dni.get()
        if not dni:
            messagebox.showwarning("Dato Faltante", "Por favor ingrese un DNI.")
            return
        body = {"dni": dni}
        self.ejecutar_rpc("reniec.validar", body)

    def consultar_saldo(self):
        cliente_id = self.banco_cliente_id.get()
        if not cliente_id:
            messagebox.showwarning("Dato Faltante", "Por favor ingrese un ID de Cliente.")
            return
        body = {"idCliente": cliente_id}
        self.ejecutar_rpc("banco.consulta.saldo", body)

# --- Arranque de la Aplicación ---
if __name__ == "__main__":
    root = tk.Tk()
    app = App(root)
    root.mainloop()