package es.florida.vet;

import static org.junit.jupiter.api.Assertions.assertNull;
import es.florida.vet.entity.Producto;
import es.florida.vet.dto.PagoIniciadoResponse;
import es.florida.vet.dto.LineaPedidoRequest;
import es.florida.vet.dto.LineasRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.florida.vet.dto.FirmaRequest;
import es.florida.vet.dto.NotificacionPagoRequest;
import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.entity.Auxiliar;
import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.Firma;
import es.florida.vet.entity.Pago;
import es.florida.vet.entity.Pedido;
import es.florida.vet.entity.Usuario;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.enums.Rol;
import es.florida.vet.exception.AccesoDenegadoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.AuxiliarRepository;
import es.florida.vet.repository.FirmaRepository;
import es.florida.vet.repository.MovimientoStockRepository;
import es.florida.vet.repository.PagoRepository;
import es.florida.vet.repository.PedidoRepository;
import es.florida.vet.repository.ProductoRepository;
import es.florida.vet.repository.VeterinarioRepository;
import es.florida.vet.service.PagoService;
import es.florida.vet.service.PedidoActualizadoEvent;
import es.florida.vet.service.PedidoService;
import es.florida.vet.service.SesionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoFirmaPagoTest {

    private static final Long PEDIDO_ID = 100L;
    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final String COLEGIADO_RESPONSABLE = "COL-100";

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private VeterinarioRepository veterinarioRepository;
    @Mock
    private AuxiliarRepository auxiliarRepository;
    @Mock
    private MovimientoStockRepository movimientoRepository;
    @Mock
    private FirmaRepository firmaRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private es.florida.vet.repository.BultoRepository bultoRepository;
    @Mock
    private SesionService sesionService;
    @Mock
    private ApplicationEventPublisher eventos;

    private PedidoService pedidoService;

    @BeforeEach
    void prepararServicio() {
        pedidoService = new PedidoService(pedidoRepository, productoRepository, veterinarioRepository,
                auxiliarRepository, movimientoRepository, firmaRepository, bultoRepository, sesionService, eventos);
        lenient().when(pedidoRepository.save(any(Pedido.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(firmaRepository.save(any(Firma.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(pagoRepository.save(any(Pago.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("un auxiliar de la clínica no puede firmar aunque vea el pedido")
    void auxiliarNoPuedeFirmar() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        Usuario usuarioAuxiliar = usuario(31L, "ana.aux", "Ana Auxiliar", Rol.AUXILIAR);
        new Auxiliar(usuarioAuxiliar, contexto.clinica());
        permitirAccesoFirma(contexto, usuarioAuxiliar, true);

        assertThrows(AccesoDenegadoException.class,
                () -> pedidoService.firmar(PEDIDO_ID, solicitudFirma(null), usuarioAuxiliar.getUsername()));

        verify(firmaRepository, never()).save(any(Firma.class));
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
        assertNull(contexto.pedido().getFechaFirma());
    }

    @Test
    @DisplayName("el veterinario asignado firma con su identidad y queda guardada la copia del pedido")
    void veterinarioAsignadoFirma() throws Exception {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        permitirFirma(contexto, contexto.responsable());

        PedidoResponse respuesta = pedidoService.firmar(PEDIDO_ID, solicitudFirma(null),
                contexto.responsable().getUsername());

        ArgumentCaptor<Firma> firmaGuardada = ArgumentCaptor.forClass(Firma.class);
        verify(firmaRepository).save(firmaGuardada.capture());
        JsonNode copia = new ObjectMapper().readTree(firmaGuardada.getValue().getContenidoPedido());
        assertSame(contexto.responsable(), firmaGuardada.getValue().getUsuario());
        assertEquals("VETERINARIO", copia.path("firmanteRol").asText());
        assertEquals(EstadoPedido.FIRMADO.name(), respuesta.estado());
        assertArrayEquals(java.util.Base64.getDecoder().decode(PNG_1X1), firmaGuardada.getValue().getImagen());
    }

    @Test
    @DisplayName("un veterinario de la clínica que no es el responsable asignado no puede firmar")
    void veterinarioNoResponsableNoPuedeFirmar() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        Usuario veterinarioNoResponsable = usuario(41L, "vet.otro", "Dr. Otro", Rol.VETERINARIO);
        new Veterinario(veterinarioNoResponsable, contexto.clinica(), "COL-200", "600000200");
        permitirAccesoFirma(contexto, veterinarioNoResponsable, true);

        assertThrows(AccesoDenegadoException.class,
                () -> pedidoService.firmar(PEDIDO_ID, solicitudFirma(null), veterinarioNoResponsable.getUsername()));

        verify(firmaRepository, never()).save(any(Firma.class));
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
    }

    @Test
    @DisplayName("un administrador no puede usar su cuenta para firmar directamente")
    void administradorNoPuedeFirmarDirectamente() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        Usuario administrador = usuario(51L, "admin", "Admin Almacén", Rol.ADMIN);
        permitirAccesoFirma(contexto, administrador, true);

        assertThrows(AccesoDenegadoException.class,
                () -> pedidoService.firmar(PEDIDO_ID, solicitudFirma(null), administrador.getUsername()));

        verify(firmaRepository, never()).save(any(Firma.class));
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
    }

    @Test
    @DisplayName("la sesión de otra clínica queda denegada antes de consultar o guardar la firma")
    void accesoDeOtraClinicaEsDenegadoPorLaSesion() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        Clinica clinicaAjena = new Clinica("Clínica Ajena", "Calle Dos", "600000002");
        asignarId(clinicaAjena, 11L);
        Usuario usuarioAjeno = usuario(61L, "aux.ajena", "Auxiliar Ajena", Rol.AUXILIAR);
        Auxiliar auxiliarAjeno = new Auxiliar(usuarioAjeno, clinicaAjena);
        asignarId(auxiliarAjeno, 62L);

        when(pedidoRepository.findById(PEDIDO_ID)).thenReturn(Optional.of(contexto.pedido()));
        when(sesionService.usuarioActual(usuarioAjeno.getUsername())).thenReturn(usuarioAjeno);
        when(sesionService.puedeAccederAClinica(usuarioAjeno, false, contexto.clinica().getId())).thenReturn(false);

        assertThrows(AccesoDenegadoException.class,
                () -> pedidoService.firmar(PEDIDO_ID, solicitudFirma(null), usuarioAjeno.getUsername()));

        verify(sesionService).puedeAccederAClinica(usuarioAjeno, false, contexto.clinica().getId());
        verifyNoInteractions(firmaRepository);
    }

    @Test
    @DisplayName("un número colegiado informado distinto al del responsable rechaza la confirmación")
    void numeroColegiadoDistintoAlResponsableRechazaLaFirma() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        permitirFirma(contexto, contexto.responsable());
        FirmaRequest conColegiadoDistinto = new FirmaRequest("DIBUJO", PNG_1X1, null, true, "COL-OTRO");

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> pedidoService.firmar(PEDIDO_ID, conColegiadoDistinto, contexto.responsable().getUsername()));

        assertTrue(error.getMessage().contains("no coincide"));
        verify(firmaRepository, never()).save(any(Firma.class));
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
    }

    @Test
    @DisplayName("el número colegiado no sustituye ni la imagen PNG ni la confirmación propia")
    void numeroColegiadoNoSustituyeImagenNiConfirmacion() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        permitirFirma(contexto, contexto.responsable());
        FirmaRequest sinImagen = new FirmaRequest("DIBUJO", "", null, true, COLEGIADO_RESPONSABLE);
        FirmaRequest sinConfirmacion = new FirmaRequest("DIBUJO", PNG_1X1, null, false, COLEGIADO_RESPONSABLE);

        assertThrows(ReglaNegocioException.class,
                () -> pedidoService.firmar(PEDIDO_ID, sinImagen, contexto.responsable().getUsername()));
        assertThrows(ReglaNegocioException.class,
                () -> pedidoService.firmar(PEDIDO_ID, sinConfirmacion, contexto.responsable().getUsername()));

        verify(firmaRepository, never()).save(any(Firma.class));
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
    }

    @Test
    @DisplayName("un pago confirmado con firma HMAC válida deja el pedido firmado listo para preparar")
    void pagoConfirmadoDePedidoFirmadoPasaAListoPreparar() {
        ContextoPedido contexto = pedidoFirmado(PEDIDO_ID);
        Pago pago = new Pago(contexto.pedido(), BigDecimal.ZERO, "SIM-100", "t");
        PagoService pagoService = servicioPago();
        when(pagoRepository.bloquearPorReferencia("SIM-100")).thenReturn(Optional.of(pago));
        when(pedidoRepository.findById(PEDIDO_ID)).thenReturn(Optional.of(contexto.pedido()));

        String cuerpo = notificacionJson("SIM-100", "0", "EUR", "CONFIRMADO");
        pagoService.notificacion(cuerpo, pagoService.firmarCuerpo(cuerpo));

        ArgumentCaptor<PedidoActualizadoEvent> evento = ArgumentCaptor.forClass(PedidoActualizadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals(PEDIDO_ID, evento.getValue().pedidoId());
        assertNotNull(contexto.pedido().getFechaPago());
        assertNotNull(contexto.pedido().getFechaListoPreparar());
        assertEquals(EstadoPedido.LISTO_PREPARAR, contexto.pedido().getEstado());
        assertEquals(es.florida.vet.enums.EstadoPago.CONFIRMADO, pago.getEstado());
    }

    @Test
    @DisplayName("no se puede iniciar el pago de un pedido pendiente de firma")
    void noSePuedePagarSinFirma() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        permitirAccesoFirma(contexto, contexto.responsable(), true);

        assertThrows(ReglaNegocioException.class,
                () -> servicioPago().iniciar(PEDIDO_ID, contexto.responsable().getUsername(), false));

        verify(pagoRepository, never()).save(any(Pago.class));
    }

    @Test
    @DisplayName("administración no puede iniciar ni completar el pago en nombre de la clínica")
    void administracionNoPaga() {
        PagoService pagoService = servicioPago();

        assertThrows(AccesoDenegadoException.class, () -> pagoService.iniciar(PEDIDO_ID, "admin", true));
        assertThrows(AccesoDenegadoException.class,
                () -> pagoService.simuladoProcesar("SIM-1", "CONFIRMADO", "admin", true));
        verifyNoInteractions(pagoRepository);
    }

    @Test
    @DisplayName("la firma deja el pedido FIRMADO (pendiente de pago) sin pasarlo a preparación")
    void firmarDejaPedidoFirmadoPendienteDePago() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        permitirFirma(contexto, contexto.responsable());

        pedidoService.firmar(PEDIDO_ID, solicitudFirma(COLEGIADO_RESPONSABLE), contexto.responsable().getUsername());

        assertEquals(EstadoPedido.FIRMADO, contexto.pedido().getEstado());
        assertNotNull(contexto.pedido().getFechaFirma());
        assertNull(contexto.pedido().getFechaListoPreparar());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("una notificación con firma HMAC incorrecta se rechaza sin tocar el pago")
    void notificacionConFirmaIncorrectaSeRechaza() {
        PagoService pagoService = servicioPago();
        String cuerpo = notificacionJson("SIM-100", "10.00", "EUR", "CONFIRMADO");

        assertThrows(AccesoDenegadoException.class, () -> pagoService.notificacion(cuerpo, "firma-falsa"));
        assertThrows(AccesoDenegadoException.class, () -> pagoService.notificacion(cuerpo, null));
        // Cambiar el importe después de firmar invalida la firma
        String firmaOriginal = pagoService.firmarCuerpo(cuerpo);
        String manipulado = notificacionJson("SIM-100", "0.01", "EUR", "CONFIRMADO");
        assertThrows(AccesoDenegadoException.class, () -> pagoService.notificacion(manipulado, firmaOriginal));

        verify(pagoRepository, never()).bloquearPorReferencia(any());
    }

    @Test
    @DisplayName("importe o moneda distintos dejan el pago FALLADO y el pedido sin pagar")
    void importeOMonedaIncorrectosMarcanFallado() {
        ContextoPedido contexto = pedidoFirmado(PEDIDO_ID);
        Pago pago = new Pago(contexto.pedido(), BigDecimal.ZERO, "SIM-200", "t");
        PagoService pagoService = servicioPago();
        when(pagoRepository.bloquearPorReferencia("SIM-200")).thenReturn(Optional.of(pago));

        String cuerpo = notificacionJson("SIM-200", "0", "USD", "CONFIRMADO");
        assertThrows(ReglaNegocioException.class, () -> pagoService.notificacion(cuerpo, pagoService.firmarCuerpo(cuerpo)));

        assertEquals(es.florida.vet.enums.EstadoPago.FALLADO, pago.getEstado());
        assertNull(contexto.pedido().getFechaPago());
        assertEquals(EstadoPedido.FIRMADO, contexto.pedido().getEstado());
    }

    @Test
    @DisplayName("una notificación repetida no vuelve a aplicar el pago")
    void notificacionRepetidaEsIdempotente() {
        ContextoPedido contexto = pedidoFirmado(PEDIDO_ID);
        Pago pago = new Pago(contexto.pedido(), BigDecimal.ZERO, "SIM-300", "t");
        pago.setEstado(es.florida.vet.enums.EstadoPago.CONFIRMADO);
        PagoService pagoService = servicioPago();
        when(pagoRepository.bloquearPorReferencia("SIM-300")).thenReturn(Optional.of(pago));

        String cuerpo = notificacionJson("SIM-300", "0", "EUR", "CONFIRMADO");
        pagoService.notificacion(cuerpo, pagoService.firmarCuerpo(cuerpo));

        assertEquals(1, pago.getIntentos());
        assertNull(contexto.pedido().getFechaPago());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("la caducidad cancela pedidos sin pago, libera la reserva y respeta los pagos en curso")
    void caducidadCancelaYLiberaReserva() {
        ContextoPedido vencido = pedidoPendiente(401L);
        ContextoPedido conPagoEnCurso = pedidoPendiente(402L);
        Producto producto = new Producto();
        asignarId(producto, 7L);
        producto.setNombre("Guantes");
        producto.setPrecio(new BigDecimal("12.50"));
        vencido.pedido().agregarLinea(producto, 3);
        when(pedidoRepository.findByEstadoInAndFechaPagoIsNullAndFechaEnvioFirmaBefore(any(), any()))
                .thenReturn(java.util.List.of(vencido.pedido(), conPagoEnCurso.pedido()));

        int cancelados = pedidoService.caducarReservasVencidas(LocalDateTime.now().minusHours(72),
                p -> p.getId().equals(402L));

        assertEquals(1, cancelados);
        assertEquals(EstadoPedido.CANCELADO, vencido.pedido().getEstado());
        assertTrue(vencido.pedido().getMotivoCancelacion().startsWith("Reserva caducada"));
        verify(productoRepository).liberarReserva(7L, 3);
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, conPagoEnCurso.pedido().getEstado());
    }

    @Test
    @DisplayName("la preparación se bloquea aunque figure LISTO_PREPARAR si falta el pago o la firma")
    void preparacionSeBloqueaSiFaltaPagoOFirmaAunqueEstadoSeaListo() {
        ContextoPedido sinFirma = pedidoPendiente(101L);
        sinFirma.pedido().setEstado(EstadoPedido.LISTO_PREPARAR);
        sinFirma.pedido().setFechaPago(LocalDateTime.now());
        ContextoPedido sinPago = pedidoPendiente(102L);
        sinPago.pedido().setEstado(EstadoPedido.LISTO_PREPARAR);
        sinPago.pedido().setFechaFirma(LocalDateTime.now());
        when(pedidoRepository.findById(101L)).thenReturn(Optional.of(sinFirma.pedido()));
        when(pedidoRepository.findById(102L)).thenReturn(Optional.of(sinPago.pedido()));

        assertThrows(ReglaNegocioException.class,
                () -> pedidoService.cambiarEstadoInterno(101L, EstadoPedido.EN_PREPARACION, "admin"));
        assertThrows(ReglaNegocioException.class,
                () -> pedidoService.cambiarEstadoInterno(102L, EstadoPedido.EN_PREPARACION, "admin"));

        assertEquals(EstadoPedido.LISTO_PREPARAR, sinFirma.pedido().getEstado());
        assertEquals(EstadoPedido.LISTO_PREPARAR, sinPago.pedido().getEstado());
    }

    @Test
    @DisplayName("marcar pagado es idempotente cuando ya existe fecha de pago y no vuelve a publicar evento")
    void marcarPagadoConFechaExistenteEsIdempotenteYNoPublicaOtroEvento() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        LocalDateTime fechaPagoOriginal = LocalDateTime.now().minusHours(1);
        contexto.pedido().setFechaPago(fechaPagoOriginal);
        when(pedidoRepository.findById(PEDIDO_ID)).thenReturn(Optional.of(contexto.pedido()));

        pedidoService.marcarPagado(PEDIDO_ID);

        assertSame(fechaPagoOriginal, contexto.pedido().getFechaPago());
        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("un pedido pagado no se puede cancelar, tampoco desde administración")
    void pedidoPagadoNoSeCancela() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        contexto.pedido().setFechaPago(java.time.LocalDateTime.now());
        when(pedidoRepository.findById(PEDIDO_ID)).thenReturn(Optional.of(contexto.pedido()));

        assertThrows(ReglaNegocioException.class, () -> pedidoService.cancelar(PEDIDO_ID, "admin", true));

        assertEquals(EstadoPedido.PENDIENTE_FIRMA, contexto.pedido().getEstado());
        verify(productoRepository, never()).liberarReserva(anyLong(), anyInt());
    }

    @Test
    @DisplayName("si administración cambia las líneas de un borrador queda registrado quién lo hizo")
    void modificarLineasRegistraQuienLoHace() {
        ContextoPedido contexto = pedidoPendiente(PEDIDO_ID);
        contexto.pedido().setEstado(EstadoPedido.BORRADOR);
        Usuario administrador = usuario(51L, "admin", "Admin Almacén", Rol.ADMIN);
        Producto producto = new Producto();
        asignarId(producto, 7L);
        producto.setNombre("Guantes");
        producto.setPrecio(new BigDecimal("12.50"));
        when(pedidoRepository.findById(PEDIDO_ID)).thenReturn(Optional.of(contexto.pedido()));
        when(sesionService.usuarioActual("admin")).thenReturn(administrador);
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));

        PedidoResponse respuesta = pedidoService.modificarLineas(PEDIDO_ID,
                new LineasRequest(java.util.List.of(new LineaPedidoRequest(7L, 3))), "admin", true);

        assertSame(administrador, contexto.pedido().getModificadoPor());
        assertNotNull(contexto.pedido().getFechaModificacion());
        assertEquals("Admin Almacén", respuesta.modificadoPorNombre());
        assertEquals(1, contexto.pedido().getLineas().size());
    }

    @Test
    @DisplayName("iniciar el pago dos veces devuelve el mismo pago pendiente y no crea otro cobro")
    void iniciarPagoDosVecesReutilizaElPendiente() {
        ContextoPedido contexto = pedidoFirmado(PEDIDO_ID);
        Pago pendiente = new Pago(contexto.pedido(), contexto.pedido().getTotal(), "SIM-ABC", "token");
        PagoService pagoService = servicioPago();
        permitirAccesoFirma(contexto, contexto.responsable(), true);
        when(pagoRepository.findByPedidoIdOrderByFechaInicioDesc(PEDIDO_ID)).thenReturn(java.util.List.of(pendiente));

        PagoIniciadoResponse respuesta = pagoService.iniciar(PEDIDO_ID, contexto.responsable().getUsername(), false);

        assertEquals("SIM-ABC", respuesta.referencia());
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    private ContextoPedido pedidoPendiente(Long pedidoId) {
        Usuario responsable = usuario(21L, "vet.responsable", "Dra. Responsable", Rol.VETERINARIO);
        Clinica clinica = new Clinica("Clínica Norte", "Calle Uno", "600000001");
        asignarId(clinica, 10L);
        Veterinario veterinario = new Veterinario(responsable, clinica, COLEGIADO_RESPONSABLE, "600000100");
        asignarId(veterinario, 20L);
        clinica.setVeterinarioResponsable(veterinario);
        Pedido pedido = new Pedido(clinica, veterinario, responsable);
        asignarId(pedido, pedidoId);
        pedido.setEstado(EstadoPedido.PENDIENTE_FIRMA);
        return new ContextoPedido(pedido, clinica, veterinario, responsable);
    }

    private ContextoPedido pedidoFirmado(Long pedidoId) {
        ContextoPedido contexto = pedidoPendiente(pedidoId);
        contexto.pedido().setEstado(EstadoPedido.FIRMADO);
        contexto.pedido().setFechaFirma(LocalDateTime.now().minusMinutes(5));
        return contexto;
    }

    private PagoService servicioPago() {
        PagoService pagoService = new PagoService(pagoRepository, pedidoRepository, pedidoService, sesionService);
        ReflectionTestUtils.setField(pagoService, "secretoWebhook", "secreto-de-pruebas");
        return pagoService;
    }

    private String notificacionJson(String referencia, String importe, String moneda, String resultado) {
        return "{\"referencia\":\"" + referencia + "\",\"importe\":" + importe
                + ",\"moneda\":\"" + moneda + "\",\"resultado\":\"" + resultado + "\"}";
    }

    private Usuario usuario(Long id, String username, String nombreCompleto, Rol rol) {
        Usuario usuario = new Usuario();
        asignarId(usuario, id);
        usuario.setUsername(username);
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setRol(rol);
        return usuario;
    }

    private void permitirFirma(ContextoPedido contexto, Usuario firmante) {
        permitirAccesoFirma(contexto, firmante, true);
        when(firmaRepository.findByPedidoId(contexto.pedido().getId())).thenReturn(Optional.empty());
    }

    private void permitirAccesoFirma(ContextoPedido contexto, Usuario usuario, boolean permitido) {
        when(pedidoRepository.findById(contexto.pedido().getId())).thenReturn(Optional.of(contexto.pedido()));
        when(sesionService.usuarioActual(usuario.getUsername())).thenReturn(usuario);
        when(sesionService.puedeAccederAClinica(eq(usuario), eq(false), eq(contexto.clinica().getId())))
                .thenReturn(permitido);
    }

    private FirmaRequest solicitudFirma(String numeroColegiado) {
        return new FirmaRequest("DIBUJO", PNG_1X1, null, true, numeroColegiado);
    }

    private void asignarId(Object entidad, Long id) {
        ReflectionTestUtils.setField(entidad, "id", id);
    }

    private record ContextoPedido(Pedido pedido, Clinica clinica, Veterinario veterinario, Usuario responsable) {
    }
}
