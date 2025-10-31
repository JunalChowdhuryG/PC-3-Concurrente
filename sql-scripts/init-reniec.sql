-- Tabla Personas 
CREATE TABLE Personas (
    DNI VARCHAR(8) PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apell_pat VARCHAR(100) NOT NULL,
    apell_mat VARCHAR(100) NOT NULL,
    fecha_nac DATE NOT NULL,
    sexo CHAR(1),
    estado_civil VARCHAR(50),
    direccion VARCHAR(255)
);

-- Insertar datos de ejemplo de la imagen del PDF
-- (Se infieren los apellidos de la imagen)
INSERT INTO Personas (DNI, apell_pat, apell_mat, nombres, fecha_nac, sexo, direccion) VALUES
('45679812', 'GARCIA', 'FLORES', 'MARIA ELENA', '1990-07-15', 'F', 'Universitaria 1234'),
('78901234', 'RAMIREZ', 'QUISPE', 'JUAN CARLOS', '1985-03-22', 'M', 'San Martin 456'),
('12345678', 'TORRES', 'MENDOZA', 'LUIS ALBERTO', '1992-11-05', 'M', 'Samayhuaman 789'),
('23456789', 'CHAVEZ', 'ROJAS', 'ANA SOFIA', '1998-06-30', 'F', 'Huancayo 121'),
('34567890', 'PEREZ', 'VASQUEZ', 'CARLOS JUAN', '1979-12-20', 'M', 'Las Palmeras 101');