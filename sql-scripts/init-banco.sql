-- Tabla Cuentas
CREATE TABLE Cuentas (
    id_cuenta VARCHAR(10) PRIMARY KEY,
    id_cliente VARCHAR(10) NOT NULL,
    saldo DECIMAL(10, 2) NOT NULL,
    fecha_apertura DATE NOT NULL
);

-- Tabla Prestamos
CREATE TABLE Prestamos (
    id_prestamo VARCHAR(10) PRIMARY KEY,
    id_cliente VARCHAR(10) NOT NULL,
    monto_total DECIMAL(10, 2) NOT NULL,
    monto_pendiente DECIMAL(10, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_solicitud DATE NOT NULL
);

-- Tabla Transacciones
CREATE TABLE Transacciones (
    id_transaccion VARCHAR(10) PRIMARY KEY,
    id_cuenta VARCHAR(10) NOT NULL REFERENCES Cuentas(id_cuenta),
    tipo VARCHAR(20) NOT NULL,
    monto DECIMAL(10, 2) NOT NULL,
    fecha DATE NOT NULL
);

-- Insertar datos de ejemplo
INSERT INTO Cuentas (id_cuenta, id_cliente, saldo, fecha_apertura) VALUES
('CU001', 'CL001', 2500.00, '2023-01-15'),
('CU002', 'CL002', 1506.50, '2023-08-22'),
('CU003', 'CL003', 980.75, '2023-06-10');

INSERT INTO Prestamos (id_prestamo, id_cliente, monto_total, monto_pendiente, estado, fecha_solicitud) VALUES
('PR001', 'CL001', 10000.00, 8000.00, 'activo', '2023-01-20'),
('PR002', 'CL002', 5000.00, 2500.00, 'activo', '2023-03-25'),
('PR003', 'CL003', 7500.00, 0.00, 'pagado', '2023-05-10');

INSERT INTO Transacciones (id_transaccion, id_cuenta, tipo, monto, fecha) VALUES
('TR001', 'CU001', 'deposito', 500.00, '2023-02-14'),
('TR002', 'CU002', 'retiro', 200.00, '2023-04-01'),
('TR003', 'CU001', 'deposito', 300.00, '2023-06-15');