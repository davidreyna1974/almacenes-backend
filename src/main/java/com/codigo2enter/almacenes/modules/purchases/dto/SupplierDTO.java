package com.codigo2enter.almacenes.modules.purchases.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO unificado para la entidad Supplier.
 *
 * Sirve tanto como objeto de entrada (request) para crear y actualizar
 * proveedores, como objeto de salida (response) para devolverlos al cliente.
 * Mismo patrón que CategoryDTO en el módulo inventory.
 *
 * El campo 'id' es null en requests de creación — PostgreSQL lo genera
 * automáticamente. Se incluye para que la misma clase sirva como respuesta
 * con el id asignado tras la persistencia.
 *
 * El campo 'active' no se recibe en la creación (el servicio lo inicializa
 * en true), pero sí se incluye para exponerlo en la respuesta y permitir
 * activaciones/desactivaciones desde el frontend en el PUT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {

    /** Identificador generado por la base de datos. Null en requests de creación. */
    private Long id;

    /** RFC fiscal mexicano — identificador único ante el SAT.
     *  El servicio valida unicidad antes de crear o actualizar. */
    @NotBlank(message = "El RFC es obligatorio")
    @Size(max = 13, message = "El RFC no puede exceder 13 caracteres")
    private String rfc;

    /** Razón social del proveedor.
     *  El servicio valida que no exista otro proveedor con la misma razón social. */
    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 150, message = "La razón social no puede exceder 150 caracteres")
    private String companyName;

    /** Nombre de la persona de contacto en el proveedor. Opcional. */
    @Size(max = 100, message = "El nombre de contacto no puede exceder 100 caracteres")
    private String contactName;

    /** Teléfono de contacto. Opcional. */
    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String phone;

    /** Correo electrónico de contacto.
     *  La BD garantiza unicidad con constraint uq_supplier_email.
     *  El servicio también valida unicidad para devolver un mensaje claro. */
    @Email(message = "El formato del correo electrónico no es válido")
    @Size(max = 100, message = "El correo no puede exceder 100 caracteres")
    private String email;

    /** Dirección física del proveedor. Opcional. */
    private String address;

    /** Estado del proveedor. true = activo, false = dado de baja.
     *  Incluir en PUT para activar/desactivar el proveedor. */
    private boolean active;

    /** Campos de auditoría — solo de salida, el cliente nunca los envía en request. */
    private java.time.LocalDateTime createdAt;
    private Long   createdById;
    private String createdByUsername;
    private java.time.LocalDateTime updatedAt;
    private Long   updatedById;
    private String updatedByUsername;
}
