# Web pública y solicitudes de alta de clínicas

## El recorrido

```
Formulario por pasos (datos + imagen acreditativa) → backend VetStock → MySQL → Odoo (revisión de una persona)
                                                   │ decisión firmada
                                                   ▼
                                  backend: alta en MySQL y contactos en Odoo
                                                   │
                                  administración genera el enlace de activación
                                                   │
                                  el veterinario elige su contraseña y entra
```

Enviar el formulario **no** crea una clínica, ni una cuenta, ni un usuario de Odoo, ni un pedido. Solo guarda una solicitud pendiente y da una referencia (`SOL-…`).

Reparto de responsabilidades, para que un dato no tenga dos dueños:

| Dato | Responsable |
|---|---|
| Datos de la solicitud y estado del alta | VetStock |
| Decisión de revisión | Odoo (la persona que revisa) |
| Clínicas y veterinarios (MySQL) | VetStock; Odoo nunca escribe en MySQL |
| Contactos en Odoo | VetStock los crea mediante la integración |

## El formulario por pasos

Es un único formulario dividido en tres pasos, con un indicador de tres círculos y el texto "Paso N de 3":

1. **Clínica.** Los mismos campos y reglas de siempre.
2. **Veterinario responsable y acreditación.** Los datos del veterinario y la imagen obligatoria.
3. **Revisión y envío.** Un resumen con botones "Editar", las observaciones y la confirmación de privacidad.

- "Siguiente" solo valida el paso actual, muestra los errores junto a cada campo y lleva el foco al primero. No envía nada.
- La solicitud solo se envía con "Enviar solicitud" en el paso 3. Pulsar Enter en un paso intermedio equivale a "Siguiente".
- Los datos y la imagen se conservan al ir y volver entre pasos, mientras la página siga abierta. No se guardan en el navegador (localStorage), así que si se recarga la página hay que volver a adjuntar la imagen.

## Documento acreditativo

Es una imagen JPG o PNG de un documento que identifique a la clínica y al veterinario y acredite su relación. No es una fotografía del veterinario ni una firma, y no se pide el DNI.

**Comprobaciones en el servidor (`ValidadorImagen`).** No se fía de la extensión, del tipo declarado ni del atributo `accept` del navegador:

- Tamaño máximo de 5 MB (`SOLICITUDES_DOCUMENTO_MAX_MB`).
- Los primeros bytes deben ser de PNG o JPG y coincidir con la extensión y el tipo declarado. Un SVG, un ejecutable o un texto renombrado se rechazan.
- Las dimensiones se leen de la cabecera **antes** de decodificar la imagen: entre 200 y 8000 píxeles por lado y como máximo 40 megapíxeles. Así una imagen desproporcionada no agota la memoria.
- La imagen se decodifica entera para descartar archivos dañados.

**Dónde se guarda.** En MySQL (`solicitud_documentos`, columna `MEDIUMBLOB`), igual que las firmas y los PDF de los pedidos. No hay carpeta pública ni nombres de archivo en disco: el nombre original solo se muestra.

La solicitud y la imagen se guardan en la misma transacción. Si algo falla, no queda una solicitud sin documento presentada como recibida ni una imagen huérfana.

**Quién la ve.**

- En VetStock, solo administración, en `GET /api/solicitudes-alta/{id}/documento`, con `no-store` y `nosniff`.
- En Odoo, como adjunto privado del registro (`public: false`), con los mismos permisos que la solicitud: revisores e integración.

No se hace OCR, reconocimiento facial ni verificación con IA.

## Revisión manual del documento en Odoo

El documento tiene su propio estado:

| Estado | Significado |
|---|---|
| Pendiente de revisión | Hay imagen y nadie la ha aceptado |
| Revisado y aceptado | Una persona pulsó "Aceptar documento"; se guardan quién, cuándo y la nota |
| Anterior, sin acreditación | Solicitud creada antes de esta función |
| Excepción autorizada | Solicitud anterior aprobable sin documento, con el motivo registrado |

- **Aprobar** solo aparece, y el backend solo lo admite, con el documento aceptado o con una excepción autorizada. Tener una imagen no basta.
- **Aceptar documento** envía el id del documento que se ha visto. Si administración lo ha sustituido mientras tanto, el backend lo rechaza y hay que revisar el nuevo.
- **Rechazar** exige escribir el motivo en las observaciones internas.
- La imagen se ve en la ficha, se puede ampliar y se descarga desde el enlace "Descargar".

**Si el documento no sirve**, se pulsa "Requiere información adicional". La clínica envía otro por el canal habitual de la distribuidora, ya que no hay enlace público de edición. Administración lo adjunta en **VetStock → Solicitudes de alta → Sustituir**. El anterior se conserva (`vigente = false`), la revisión del documento vuelve a "Pendiente" y en Odoo se añade un adjunto nuevo. Una solicitud aprobada o rechazada no admite sustituciones.

**Solicitudes anteriores.** Quedan como "Anterior, sin acreditación". Se siguen viendo y no se aprueban hasta que administración adjunte un documento o una persona autorice una excepción con su motivo. No se borran ni se dan por verificadas.

## Estados: tres cosas distintas

- **Revisión** (la decide una persona): pendiente, requiere información, aprobada, rechazada.
- **Integración o alta** (la calcula el backend): no iniciada, en proceso, requiere revisión, fallida, completada.
- **Acceso** (cuenta del veterinario): sin cuenta, pendiente, activo.

Una clínica puede estar aprobada y registrada y tener aún el acceso pendiente.

## Qué pasa al aprobar

1. Odoo envía la decisión firmada con HMAC-SHA256 y una marca de tiempo (caduca a los 5 minutos). El secreto y la URL son variables de entorno del servidor de Odoo.
2. El backend comprueba firma, marca de tiempo, referencia y estado.
3. En una transacción de MySQL (`AltaClinicaService`) bloquea la solicitud y busca duplicados:

| Situación | Resultado |
|---|---|
| Identificador fiscal y colegiado ya están juntos en la misma clínica | Se vincula, sin modificar nada |
| El identificador fiscal existe con otro veterinario | Requiere revisión, sin crear ni modificar nada |
| El colegiado está en otra clínica | Requiere revisión |
| Ya existe una clínica con ese nombre | Requiere revisión (el nombre solo avisa, no identifica) |
| Nada coincide | Crea la clínica, el veterinario y la relación de responsable |

4. Fuera de esa transacción crea en Odoo la clínica (empresa) y el veterinario (persona relacionada, sin usuario interno), guardando cada id en cuanto lo tiene.
5. Solo cuando todo ha terminado marca el alta como **completada**.

Entre MySQL y Odoo no hay transacción común. Por eso cada paso queda guardado y un reintento continúa donde se quedó. Aprobar dos veces o reintentar no duplica. La clínica y el veterinario usan las mismas reglas del alta manual (`ClinicaService`), que sigue funcionando.

## Si Odoo no responde

- **Al enviar el formulario:** la persona recibe su referencia igualmente. La solicitud queda pendiente con el error registrado y una tarea la reintenta cada minuto. El registro de Odoo se busca por referencia, así que no se duplica.
- **Al aprobar:** el alta queda como fallida, lo creado se conserva y aparece el botón "Reintentar alta".
- **Si el backend no responde a Odoo:** no cambia nada y Odoo avisa de que la decisión no se ha registrado.

## Probarlo

1. Abre `http://localhost:5173/`, pulsa "Solicitar alta de clínica" y envía el formulario con datos ficticios.
2. Espera hasta un minuto y abre Odoo: **Ventas → Solicitudes de alta de clínicas**.
3. Pulsa **Aprobar**, **Rechazar** o **Requiere información adicional**.
4. Comprueba el alta en VetStock (**Administración → Clínicas** y **Solicitudes**) y en Odoo (**Contactos**).
5. En **Administración → Solicitudes**, pulsa **Generar enlace**, cópialo y ábrelo. Elige una contraseña e inicia sesión con el usuario que indica.

## Actualizar el módulo de Odoo

`docker compose up -d --build odoo`. El contenedor actualiza el módulo al arrancar.

El módulo se copia en `/opt/vetstock-addons`, no en `/mnt/extra-addons`: la imagen oficial declara esta última como volumen y, al recrear el contenedor, Docker conservaba la copia antigua del módulo.

## Procedimiento manual de revisión

La aplicación no consulta registros oficiales: comprobar la veracidad de los datos es trabajo de la persona que revisa.

1. Comprueba los datos de la clínica y el número de colegiado por los medios que tenga la distribuidora.
2. Revisa el documento acreditativo y pulsa "Aceptar documento" si acredita la relación. Si no, pulsa "Requiere información adicional" y escribe la gestión en las observaciones internas. El contacto con la clínica se hace por teléfono o correo, fuera de la aplicación.
3. Decide. Rechazar es definitivo. Una solicitud con la clínica ya creada no se puede rechazar.
4. Si el alta pasa a "Requiere revisión", compruébalo en VetStock y rechaza la solicitud o, si procede, resuelve la coincidencia y pulsa "Reintentar alta". Una clínica existente nunca se sobrescribe sola.
5. Tras el alta, genera el enlace y entrégalo directamente al veterinario. No hay correo configurado.

## Permisos

- **Odoo:** para ver la sección hay que estar en el grupo "Revisor de solicitudes de alta" (Ajustes → Usuarios). Un revisor solo escribe sus observaciones; ni crea, ni borra, ni cambia estados a mano. El menú cuelga de Ventas, así que también necesita acceso a esa aplicación.
- **VetStock:** el listado y las acciones son solo para `ADMIN`. El formulario y la activación son públicos, y no hay ninguna ruta pública de listado ni de aprobación.

## Medidas del formulario

- Validación en navegador y servidor, con límites de longitud y sin formatos propios de un país.
- Reenviar lo mismo dentro de 10 minutos devuelve la misma referencia, también con envíos simultáneos.
- Límite de envíos por IP, en memoria (`SOLICITUDES_MAX_POR_HORA`). Se reinicia con el servidor y detrás de un proxy todos comparten IP: para producción habría que revisarlo.
- Campo trampa oculto contra programas, sin CAPTCHA externo.
- La respuesta solo contiene la referencia: no revela si un email o un identificador ya existen.
- Los registros no guardan datos personales completos.

## Activación de la cuenta

El enlace dura 7 días, sirve una vez y generar otro anula el anterior. En la base de datos solo se guarda el hash SHA-256 del token, y la contraseña se guarda con BCrypt. Mientras no se activa, la cuenta está inactiva y no puede iniciar sesión.

## Foto y enlaces del desarrollador

En `frontend/src/pages/publico/datosDesarrollador.js`:

1. Copia la imagen a `frontend/public/` (por ejemplo, `desarrollador.jpg`) y pon `foto: '/desarrollador.jpg'`.
2. Rellena `email`, `github` y `linkedin`. Un enlace vacío o con una URL no válida no se muestra.
3. Reconstruye el frontend: `docker compose up -d --build frontend`.

## Antes de un despliegue público

No se ha inventado ningún texto legal. Faltan por definir, por quien corresponda:

- Identidad y contacto del responsable del tratamiento, base legal y derechos de las personas.
- Plazo de conservación de las solicitudes. No hay borrado automático.
- Aviso legal y condiciones.
- Plazo de conservación de los documentos adjuntos, en VetStock y en Odoo. No se borran solos, tampoco los sustituidos.
- Si se quiere, eliminar los metadatos de las imágenes (EXIF), que ahora se guardan tal como llegan.
- HTTPS y un secreto propio en `ODOO_SECRETO_SOLICITUDES`; el valor de `docker-compose.yml` es solo para desarrollo.
- Envío del enlace de activación por correo, si se quiere dejar de entregar a mano.
