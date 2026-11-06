package es.florida.vet.service;

import es.florida.vet.entity.Clinica;
import es.florida.vet.entity.SolicitudAlta;
import es.florida.vet.entity.Veterinario;
import es.florida.vet.enums.EstadoAltaSolicitud;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.repository.ClinicaRepository;
import es.florida.vet.repository.SolicitudAltaRepository;
import es.florida.vet.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Parte LOCAL del alta de una solicitud aprobada, en una sola transacción de MySQL. Odoo va aparte
 * (ver SolicitudAltaService): entre los dos sistemas no hay transacción común.
 */
@Service
public class AltaClinicaService {

    private final SolicitudAltaRepository solicitudRepository;
    private final ClinicaRepository clinicaRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final ClinicaService clinicaService;

    public AltaClinicaService(SolicitudAltaRepository solicitudRepository, ClinicaRepository clinicaRepository,
                              VeterinarioRepository veterinarioRepository, ClinicaService clinicaService) {
        this.solicitudRepository = solicitudRepository;
        this.clinicaRepository = clinicaRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.clinicaService = clinicaService;
    }

    /**
     * Crea o vincula la clínica y su veterinario. Si la solicitud ya los tiene, no hace nada, así
     * que aprobar dos veces no duplica. Una coincidencia dudosa no crea ni modifica nada: la revisa
     * una persona. Solo se identifica por identificador fiscal y número de colegiado; el nombre
     * igual únicamente avisa.
     */
    @Transactional
    public void crearRegistrosLocales(Long solicitudId) {
        SolicitudAlta s = solicitudRepository.bloquear(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        if (s.getClinicaId() != null) {
            return;
        }

        Optional<Clinica> porFiscal = s.getIdentificadorFiscal() == null ? Optional.empty()
                : clinicaRepository.findByIdentificadorFiscal(s.getIdentificadorFiscal());
        Optional<Veterinario> porColegiado = veterinarioRepository.findByNumeroColegiado(s.getVetColegiado());

        if (porFiscal.isPresent()) {
            Clinica clinica = porFiscal.get();
            boolean mismoVeterinario = porColegiado.isPresent()
                    && porColegiado.get().getClinica().getId().equals(clinica.getId());
            if (mismoVeterinario) {
                // Ya estaban dados de alta: se vinculan sin tocar sus datos ni su responsable
                s.vincular(clinica.getId(), porColegiado.get().getId());
                s.marcarAlta(EstadoAltaSolicitud.EN_PROCESO, "Vinculada a una clínica existente");
            } else {
                s.marcarAlta(EstadoAltaSolicitud.REQUIERE_REVISION, "Ya existe una clínica con ese identificador fiscal y otro veterinario: revísalo");
            }
            return;
        }
        if (porColegiado.isPresent()) {
            // Cada veterinario pertenece a una sola clínica en este modelo
            s.marcarAlta(EstadoAltaSolicitud.REQUIERE_REVISION, "Ese número de colegiado ya está en otra clínica: revísalo");
            return;
        }
        if (clinicaRepository.findFirstByNombreIgnoreCase(s.getClinicaNombre()).isPresent()) {
            s.marcarAlta(EstadoAltaSolicitud.REQUIERE_REVISION, "Ya existe una clínica con ese nombre: comprueba si es la misma");
            return;
        }

        Clinica clinica = clinicaService.registrarClinica(s.getClinicaNombre(), direccionCompleta(s), s.getTelefono());
        clinica.completarDesdeSolicitud(s.getIdentificadorFiscal(), s.getEmail(), s.getReferencia());
        Veterinario veterinario = clinicaService.registrarVeterinarioSinAcceso(s.getVetNombre(), clinica,
                s.getVetColegiado(), s.getVetTelefono(), s.getVetEmail());
        clinica.setVeterinarioResponsable(veterinario);
        s.vincular(clinica.getId(), veterinario.getId());
        s.marcarAlta(EstadoAltaSolicitud.EN_PROCESO, "Clínica y veterinario registrados en VetStock");
    }

    private String direccionCompleta(SolicitudAlta s) {
        return s.getDireccion() + ", " + s.getCodigoPostal() + " " + s.getLocalidad() + " (" + s.getProvincia() + ")";
    }
}
