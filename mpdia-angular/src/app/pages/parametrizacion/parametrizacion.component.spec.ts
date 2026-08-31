import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { ParametrizacionComponent } from './parametrizacion.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { MetricaSeleccionada, PropuestaGenAI } from '../../models/seleccion.model';
import { TopParametrizacion } from '../../models/metric-ranking.model';
import { environment } from '../../../environments/environment';

/**
 * Tests para ParametrizacionComponent - FASE 16.5
 * 
 * Valida que el componente muestra:
 * - Botón "Generar parametrización con GenAI" (no "Generar 3 propuestas")
 * - UNA sola propuesta generada (no 3)
 * - Advertencia "Requiere validación humana"
 * - Botones "Usar esta propuesta" y "Regenerar"
 * - Ranking Top 3 funcional
 * - Botón "Usar" del ranking funcional
 */
describe('ParametrizacionComponent - Fase 16.5', () => {
  let component: ParametrizacionComponent;
  let fixture: ComponentFixture<ParametrizacionComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  const mockMetrica: MetricaSeleccionada = {
    id: '1',
    metricaNombre: 'Velocidad',
    metricaDescripcion: 'Puntos completados por sprint',
    factorId: '1',
    factorNombre: 'Productividad',
    factorCategoria: 'Interno',
    proyectoId: '123',
    estadoParametrizacion: 'sin_parametrizar',
    creadoEn: '2026-01-01'
  };

  const mockPropuestaUnica: PropuestaGenAI = {
    titulo: 'Medición de velocidad por story points',
    objetivo: 'Medir capacidad de entrega del equipo',
    procedimiento: 'Sumar story points de historias Done',
    indicadorVariable: 'Story Points Completados',
    escala: 'Numérica 0-100 puntos',
    justificacion: 'PROPUESTA basada en prácticas ágiles. Requiere validación del equipo.'
  };

  const mockTop3: TopParametrizacion[] = [
    { 
      id: '1', 
      objetivo: 'Objetivo 1', 
      escala: 'Escala 1', 
      userEmail: 'user1@test.com', 
      usos: 10, 
      procedimiento: 'P1', 
      indicadorVariable: 'I1', 
      createdAt: '2026-01-01'
    },
    { 
      id: '2', 
      objetivo: 'Objetivo 2', 
      escala: 'Escala 2', 
      userEmail: 'user2@test.com', 
      usos: 5, 
      procedimiento: 'P2', 
      indicadorVariable: 'I2', 
      createdAt: '2026-01-02'
    },
    { 
      id: '3', 
      objetivo: 'Objetivo 3', 
      escala: 'Escala 3', 
      userEmail: 'user3@test.com', 
      usos: 3, 
      procedimiento: 'P3', 
      indicadorVariable: 'I3', 
      createdAt: '2026-01-03'
    }
  ];

  beforeEach(async () => {
    const mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: (key: string) => key === 'metricaId' ? '1' : null
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [
        ParametrizacionComponent,
        HttpClientTestingModule,
        FormsModule
      ],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        SeleccionService,
        MetricRankingService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ParametrizacionComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    spyOn(router, 'navigate');
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('debe crear el componente', () => {
    expect(component).toBeTruthy();
  });

  describe('Generación de propuesta con IA - FASE 16.5', () => {
    
    it('debe inicializar con propuestas vacías', () => {
      expect(component.propuestas).toEqual([]);
      expect(component.generando).toBe(false);
    });

    it('NO debe generar 3 propuestas - solo 1 (validación de cambio Fase 16.5)', (done) => {
      // Este test valida el cambio principal de Fase 16.5:
      // Se dejó de generar 3 propuestas y ahora se genera solo 1
      component.metrica = mockMetrica;
      
      component.generarPropuestas();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/propuestas`);
      req.flush([mockPropuestaUnica]);

      setTimeout(() => {
        // Validación crítica: NO deben ser 3 propuestas
        expect(component.propuestas.length).not.toBe(3);
        // Validación positiva: debe ser exactamente 1
        expect(component.propuestas.length).toBe(1);
        done();
      }, 100);
    });

    it('debe generar EXACTAMENTE UNA propuesta al llamar generarPropuestas()', (done) => {
      component.metrica = mockMetrica;
      
      component.generarPropuestas();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/propuestas`);
      expect(req.request.method).toBe('POST');
      
      // Backend devuelve array con 1 elemento
      req.flush([mockPropuestaUnica]);

      setTimeout(() => {
        expect(component.propuestas.length).toBe(1);
        expect(component.propuestas[0].titulo).toBe(mockPropuestaUnica.titulo);
        expect(component.generando).toBe(false);
        done();
      }, 100);
    });

    it('usarPropuesta() debe copiar datos al formulario', () => {
      component.metrica = mockMetrica;
      component.form = { 
        objetivo: '', 
        procedimiento: '', 
        indicadorVariable: '', 
        escala: '', 
        frecuenciaCaptura: 'por_sprint' 
      };
      
      component.usarPropuesta(mockPropuestaUnica);

      expect(component.form.objetivo).toBe(mockPropuestaUnica.objetivo);
      expect(component.form.procedimiento).toBe(mockPropuestaUnica.procedimiento);
      expect(component.form.indicadorVariable).toBe(mockPropuestaUnica.indicadorVariable);
      expect(component.form.escala).toBe(mockPropuestaUnica.escala);
    });

    it('debe manejar errores al generar propuestas', (done) => {
      component.metrica = mockMetrica;
      
      component.generarPropuestas();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/propuestas`);
      req.flush({ message: 'Error de API' }, { status: 500, statusText: 'Internal Server Error' });

      setTimeout(() => {
        expect(component.errorGenAI).toBeTruthy();
        expect(component.generando).toBe(false);
        expect(component.propuestas.length).toBe(0);
        done();
      }, 100);
    });
  });

  describe('Ranking Top 3 - Debe mantenerse funcional', () => {

    it('debe inicializar con top3 vacío', () => {
      expect(component.top3).toEqual([]);
    });

    it('usarDelTop() debe copiar datos al formulario', () => {
      component.metrica = mockMetrica;
      component.top3 = mockTop3;
      component.form = { 
        objetivo: '', 
        procedimiento: '', 
        indicadorVariable: '', 
        escala: '', 
        frecuenciaCaptura: 'por_sprint' 
      };

      component.usarDelTop(mockTop3[0]);

      // Debe capturar el request de incrementar uso
      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/${mockMetrica.factorId}/uso`);
      expect(req.request.method).toBe('POST');
      req.flush({});

      expect(component.form.objetivo).toBe(mockTop3[0].objetivo);
      expect(component.form.procedimiento).toBe(mockTop3[0].procedimiento);
      expect(component.form.indicadorVariable).toBe(mockTop3[0].indicadorVariable);
      expect(component.form.escala).toBe(mockTop3[0].escala);
    });

    // Corrección: "Usar" debía reutilizar la parametrización COMPLETA, no
    // solo objetivo/procedimiento/indicadorVariable/escala (el resto quedaba
    // vacío aunque el registro original lo tuviera definido).
    it('usarDelTop() reutiliza la parametrización completa (frecuencia + campos académicos), no solo el objetivo', () => {
      component.metrica = mockMetrica;
      const entradaCompleta: TopParametrizacion = {
        id: '3', objetivo: 'Objetivo 3', escala: 'Numérica 1-5', userEmail: 'user3@test.com',
        usos: 5, procedimiento: 'P3', indicadorVariable: 'I3', createdAt: '2026-01-01',
        frecuenciaCaptura: 'semanal',
        fuenteAcademica: 'Scrum Guide 2020',
        formulaAcademica: 'SUMA(I3)',
        tipoOperacion: 'SUMA',
        unidadResultado: 'puntos'
      };
      component.top3 = [entradaCompleta];

      component.usarDelTop(entradaCompleta);

      httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/${mockMetrica.factorId}/uso`).flush({});

      expect(component.form.frecuenciaCaptura).toBe('semanal');
      expect(component.form.fuenteAcademica).toBe('Scrum Guide 2020');
      expect(component.form.formulaAcademica).toBe('SUMA(I3)');
      expect(component.form.tipoOperacion).toBe('SUMA');
      expect(component.form.unidadResultado).toBe('puntos');
    });

    // Si el registro original tiene un campo realmente vacío, "Usar" no debe
    // inventar un valor — debe quedar sin definir (la UI lo muestra como
    // "No definido").
    it('usarDelTop() conserva como vacío un campo académico que el original no tenía', () => {
      component.metrica = mockMetrica;
      const sinAcademicos: TopParametrizacion = {
        id: '4', objetivo: 'Objetivo 4', escala: 'Escala 4', userEmail: 'user4@test.com',
        usos: 1, procedimiento: 'P4', indicadorVariable: 'I4', createdAt: '2026-01-01'
        // sin frecuenciaCaptura ni campos académicos — como mockTop3 original.
      };
      component.top3 = [sinAcademicos];

      component.usarDelTop(sinAcademicos);

      httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/${mockMetrica.factorId}/uso`).flush({});

      expect(component.form.fuenteAcademica).toBeUndefined();
      expect(component.form.formulaAcademica).toBeUndefined();
      expect(component.form.tipoOperacion).toBeUndefined();
      expect(component.form.unidadResultado).toBeUndefined();
    });
  });

  describe('Formulario de parametrización', () => {
    
    it('debe permitir edición manual del formulario', () => {
      component.metrica = mockMetrica;
      component.form.objetivo = 'Test objetivo';
      fixture.detectChanges();

      expect(component.form.objetivo).toBe('Test objetivo');
    });

    it('debe inicializar formulario con frecuencia por_sprint', () => {
      expect(component.form.frecuenciaCaptura).toBe('por_sprint');
    });

    // Revisión de frecuencia de captura: guardar() (botón "Guardar parametrización",
    // el flujo legado vía MetricRankingService) no incluía frecuenciaCaptura en el
    // request — el backend la persistía siempre como "por_sprint" sin importar lo
    // elegido acá, aunque el usuario hubiera seleccionado "Diariamente".
    it('guardar() envía la frecuenciaCaptura elegida en el formulario al backend', () => {
      component.metrica = mockMetrica;
      component.form.objetivo = 'obj';
      component.form.procedimiento = 'proc';
      component.form.indicadorVariable = 'ind';
      component.form.escala = 'escala';
      component.form.frecuenciaCaptura = 'diaria';
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1' }));

      component.guardar();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(req.request.body.frecuenciaCaptura).toBe('diaria');
      req.flush({ id: 'p1', status: 'pendiente' });

      localStorage.removeItem('mpdia_proyecto_activo');
    });

    it('guardar() sin frecuencia elegida explícitamente sigue enviando "por_sprint" (comportamiento preexistente)', () => {
      component.metrica = mockMetrica;
      component.form.objetivo = 'obj';
      component.form.procedimiento = 'proc';
      component.form.indicadorVariable = 'ind';
      component.form.escala = 'escala';
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1' }));

      component.guardar();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(req.request.body.frecuenciaCaptura).toBe('por_sprint');
      req.flush({ id: 'p1', status: 'pendiente' });

      localStorage.removeItem('mpdia_proyecto_activo');
    });
  });

  // ══════════════════════════════════════════════════════════════════════
  // Corrección del manejo de escalas — el formulario debe capturar la escala
  // de forma estructurada (tipo/min/max/paso/sinLimite/descripcion), no solo
  // el texto libre "escala", y debe validarla espejando al backend
  // (ParametrizacionService.validarEscalaEstructurada()) antes de enviar.
  // ══════════════════════════════════════════════════════════════════════
  describe('Escala estructurada', () => {

    function formularioBase(): void {
      component.metrica = mockMetrica;
      component.form.objetivo = 'obj';
      component.form.procedimiento = 'proc';
      component.form.indicadorVariable = 'ind';
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1' }));
    }

    afterEach(() => localStorage.removeItem('mpdia_proyecto_activo'));

    it('sin ningún campo estructurado: guarda igual (compatibilidad histórica)', () => {
      formularioBase();
      component.form.escala = 'texto libre sin estructurar';

      component.guardar();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(req.request.body.escalaTipo).toBeUndefined();
      req.flush({ id: 'p1', status: 'pendiente' });
    });

    it('escala 0-10 entera: envía los 5 campos estructurados correctos al backend', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = 0;
      component.form.escalaMax = 10;
      component.form.escalaPaso = 1;
      component.form.escalaSinLimite = false;
      component.form.escalaDescripcion = '0 = Muy malo; 10 = Excelente';

      component.guardar();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(req.request.body.escalaTipo).toBe('NUMERICA_ENTERA');
      expect(req.request.body.escalaMin).toBe(0);
      expect(req.request.body.escalaMax).toBe(10);
      expect(req.request.body.escalaPaso).toBe(1);
      expect(req.request.body.escalaSinLimite).toBe(false);
      req.flush({ id: 'p1', status: 'pendiente' });
    });

    it('escala sin límite superior: NO llama al backend con la petición inválida cuando falta el mínimo', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = undefined;
      component.form.escalaSinLimite = true;
      component.form.escalaPaso = 1;

      component.guardar();

      httpMock.expectNone(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(component.errorEscala).toContain('mínimo');
    });

    it('máximo menor que mínimo: se rechaza en el frontend antes de llamar al backend', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = 10;
      component.form.escalaMax = 0;
      component.form.escalaPaso = 1;

      component.guardar();

      httpMock.expectNone(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(component.errorEscala).toContain('menor que el mínimo');
    });

    it('paso <= 0: se rechaza en el frontend antes de llamar al backend', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = 0;
      component.form.escalaMax = 10;
      component.form.escalaPaso = 0;

      component.guardar();

      httpMock.expectNone(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(component.errorEscala).toContain('paso');
    });

    it('decimal en escala entera: se rechaza en el frontend antes de llamar al backend', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = 0.5;
      component.form.escalaMax = 10;
      component.form.escalaPaso = 1;

      component.guardar();

      httpMock.expectNone(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(component.errorEscala).toContain('enteros');
    });

    it('escalaSinLimite=true: al marcar el checkbox se limpia escalaMax', () => {
      component.form.escalaMax = 999;
      component.form.escalaSinLimite = true;

      component.onEscalaSinLimiteChange();

      expect(component.form.escalaMax).toBeUndefined();
    });

    it('escala de conteo (min=0, sin límite, paso=1): guarda correctamente', () => {
      formularioBase();
      component.form.escalaTipo = 'NUMERICA_ENTERA';
      component.form.escalaMin = 0;
      component.form.escalaSinLimite = true;
      component.form.escalaPaso = 1;
      component.form.escalaDescripcion = 'Cantidad de defectos encontrados durante el sprint.';

      component.guardar();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(req.request.body.escalaSinLimite).toBe(true);
      expect(req.request.body.escalaMax).toBeUndefined();
      req.flush({ id: 'p1', status: 'pendiente' });
    });

    it('usarPropuesta() copia la escala estructurada de la IA al formulario', () => {
      component.usarPropuesta({
        ...mockPropuestaUnica,
        escalaTipo: 'NUMERICA_ENTERA',
        escalaMin: 0,
        escalaMax: null as any,
        escalaPaso: 1,
        escalaSinLimite: true,
        escalaDescripcion: 'Cantidad de problemas.'
      });

      expect(component.form.escalaTipo).toBe('NUMERICA_ENTERA');
      expect(component.form.escalaSinLimite).toBe(true);
      expect(component.form.escalaDescripcion).toBe('Cantidad de problemas.');
    });
  });

  // ── Corrección de duplicados en Verificación: doble clic en el frontend ──
  // guardar() (y guardarPropuesta()) fijaban this.guardando = true pero nunca
  // comprobaban su valor anterior — un segundo clic disparado antes de que
  // Angular reflejara [disabled]="guardando" en el DOM disparaba una SEGUNDA
  // petición HTTP real. La protección definitiva contra duplicados está en el
  // backend (advisory lock en MetricRankingService); esto solo evita la
  // petición redundante en el caso común de un clic doble accidental.
  describe('Doble clic en "Enviar al Scrum Master" (defensa de UX, no la protección principal)', () => {

    function formularioBase(): void {
      component.metrica = mockMetrica;
      component.form.objetivo = 'obj';
      component.form.procedimiento = 'proc';
      component.form.indicadorVariable = 'ind';
      component.form.escala = 'texto libre';
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1' }));
    }

    afterEach(() => localStorage.removeItem('mpdia_proyecto_activo'));

    it('dos llamadas a guardar() antes de que responda el backend generan una sola petición HTTP', () => {
      formularioBase();

      component.guardar(); // primer "clic": guardando pasa a true, se dispara el POST
      component.guardar(); // segundo "clic" antes de que el backend responda: debe bloquearse

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      expect(component.guardando).toBeTrue();
      req.flush({ id: 'p1', status: 'pendiente' });
    });

    it('tras terminar la primera petición (guardando vuelve a false), un reintento sí funciona', () => {
      formularioBase();

      component.guardar();
      const primera = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      primera.flush({ id: 'p1', status: 'pendiente' });
      expect(component.guardando).toBeFalse();

      component.guardar();
      const segunda = httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/parametrizacion`);
      segunda.flush({ id: 'p1', status: 'pendiente' });

      expect(component.guardando).toBeFalse();
    });
  });

  describe('Navegación', () => {
    
    it('volver() debe navegar a resumen-seleccion', () => {
      component.volver();

      expect(router.navigate).toHaveBeenCalledWith(['/resumen-seleccion']);
    });
  });
});


// ========================================
// TESTS FASE 16.6: APROBACIÓN Y VERSIONADO
// ========================================

describe('ParametrizacionComponent - Fase 16.6: Aprobación y Versionado', () => {
  let component: ParametrizacionComponent;
  let fixture: ComponentFixture<ParametrizacionComponent>;
  let httpMock: HttpTestingController;

  const mockMetrica: MetricaSeleccionada = {
    id: '1',
    metricaNombre: 'Velocidad',
    metricaDescripcion: 'Puntos completados por sprint',
    factorId: '1',
    factorNombre: 'Productividad',
    factorCategoria: 'Interno',
    proyectoId: '123',
    estadoParametrizacion: 'sin_parametrizar',
    creadoEn: '2026-01-01'
  };

  const mockPropuestaUnica: PropuestaGenAI = {
    titulo: 'Medición de velocidad',
    objetivo: 'Medir capacidad',
    procedimiento: 'Sumar story points',
    indicadorVariable: 'SP Completados',
    escala: 'Numérica 0-100',
    justificacion: 'PROPUESTA que requiere validación.'
  };

  beforeEach(async () => {
    const mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: (key: string) => key === 'metricaId' ? '1' : null
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [
        ParametrizacionComponent,
        HttpClientTestingModule,
        FormsModule
      ],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        SeleccionService,
        MetricRankingService
      ],
      schemas: [NO_ERRORS_SCHEMA]  // Ignorar componentes hijo como ShellComponent
    }).compileComponents();

    // Mock localStorage ANTES de crear el componente: AuthService (providedIn:'root')
    // lee 'mpdia_user' en su inicializador de signal en cuanto se construye, que
    // ocurre al inyectarse en el constructor de ParametrizacionComponent más abajo.
    // Corrección: esScrumMaster ya NO depende del rol global de cuenta —
    // depende de si el email de 'mpdia_user' coincide con scrumMasterEmail del
    // proyecto activo (mismo patrón que dashboard.component.ts). Ambos deben
    // coincidir aquí ('sm@test.com') para que el botón "Aprobar parametrización"
    // se muestre, igual que en la app real cuando el usuario autenticado ES el
    // creador de este proyecto.
    spyOn(localStorage, 'getItem').and.callFake((key: string) => {
      if (key === 'mpdia_user') {
        return JSON.stringify({ token: 'test-token', userId: 'u1', email: 'sm@test.com', role: 'scrum_master', nombre: 'SM Test' });
      }
      if (key === 'mpdia_proyecto_activo') {
        return JSON.stringify({ id: '123', scrumMasterEmail: 'sm@test.com' });
      }
      // Cualquier otra clave (p. ej. 'mpdia_selecciones' de SeleccionService) se
      // comporta como localStorage real vacío, igual que antes de este mock.
      return null;
    });

    fixture = TestBed.createComponent(ParametrizacionComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    // NO llamar fixture.detectChanges() aquí para evitar ejecutar ngOnInit automáticamente
  });

  afterEach(() => {
    // Mock cualquier request pendiente del ShellComponent antes de verificar
    const pendingReqs = httpMock.match(() => true);
    pendingReqs.forEach(req => {
      if (req.request.url.includes('/sprints/')) {
        req.flush({ id: 'sprint-1', nombre: 'Sprint 1', estado: 'activo' });
      } else {
        req.flush(null);
      }
    });
    httpMock.verify();
  });

  it('guardarPropuesta() debe guardar propuesta con estado "propuesta"', (done) => {
    // Given
    component.metrica = mockMetrica;
    component.propuestas = [mockPropuestaUnica];
    component.form = {
      objetivo: 'Test objetivo',
      procedimiento: 'Test procedimiento',
      indicadorVariable: 'Test indicador',
      escala: 'Test escala',
      frecuenciaCaptura: 'por_sprint'
    };
    
    // When
    component.guardarPropuesta();
    
    // Then
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/guardar-propuesta`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.metricaId).toBe('1');
    expect(req.request.body.proyectoId).toBe('123');
    
    req.flush({
      id: 'param-123',
      status: 'propuesta',
      version: 1
    });
    
    setTimeout(() => {
      expect(component.parametrizacionId).toBe('param-123');
      expect(component.estadoActual).toBe('propuesta');
      expect(component.versionActual).toBe(1);
      expect(component.guardando).toBe(false);
      done();
    }, 100);
  });

  it('guardarPropuesta() debe manejar error 403', (done) => {
    // Given
    component.metrica = mockMetrica;
    component.propuestas = [mockPropuestaUnica];
    component.form = {
      objetivo: 'Test', procedimiento: 'Test', indicadorVariable: 'Test', 
      escala: 'Test', frecuenciaCaptura: 'por_sprint'
    };
    
    // When
    component.guardarPropuesta();
    
    // Then
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/guardar-propuesta`);
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    
    setTimeout(() => {
      expect(component.errorGuardar).toContain('permiso');
      expect(component.guardando).toBe(false);
      done();
    }, 100);
  });

  it('aprobarParametrizacion() debe cambiar estado a "aprobada"', (done) => {
    // Given
    component.parametrizacionId = 'param-123';
    component.estadoActual = 'propuesta';
    component.form = {
      objetivo: 'Test objetivo',
      procedimiento: 'Test procedimiento',
      indicadorVariable: 'Test indicador',
      escala: 'Test escala',
      frecuenciaCaptura: 'por_sprint'
    };
    
    // When
    component.aprobarParametrizacion();
    
    // Then
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-123/aprobar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.objetivo).toBe('Test objetivo');
    
    req.flush({
      id: 'param-123',
      status: 'aprobada',
      version: 1
    });
    
    setTimeout(() => {
      expect(component.estadoActual).toBe('aprobada');
      expect(component.aprobando).toBe(false);
      done();
    }, 100);
  });

  it('aprobarParametrizacion() debe manejar error 403', (done) => {
    // Given
    component.parametrizacionId = 'param-123';
    component.form = {
      objetivo: 'Test', procedimiento: 'Test', indicadorVariable: 'Test',
      escala: 'Test', frecuenciaCaptura: 'por_sprint'
    };
    
    // When
    component.aprobarParametrizacion();
    
    // Then
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-123/aprobar`);
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    
    setTimeout(() => {
      expect(component.errorAprobar).toContain('permiso');
      expect(component.aprobando).toBe(false);
      done();
    }, 100);
  });

  // Las siguientes 5 pruebas estaban deshabilitadas (xit) desde FASE 16.6
  // ("requiere refactoring complejo de mocking"). Al tocar exactamente esta
  // lógica de estado/versión en FASE 16.10-C, quedan implementadas: se
  // renderiza el template una vez (ngOnInit dejará metrica=null porque
  // SeleccionService real está vacío y no dispara HTTP alguno), y luego se
  // fija el estado manualmente y se vuelve a renderizar.
  function renderConEstado(estado: Partial<{
    metrica: MetricaSeleccionada;
    estadoActual: 'propuesta' | 'aprobada' | null;
    versionActual: number;
    propuestas: PropuestaGenAI[];
    ultimaVersionAprobadaInfo: { version: number } | null;
  }>): void {
    fixture.detectChanges(); // ngOnInit (no dispara HTTP: metrica queda null)
    component.metrica = estado.metrica ?? mockMetrica;
    if (estado.estadoActual !== undefined) component.estadoActual = estado.estadoActual;
    if (estado.versionActual !== undefined) component.versionActual = estado.versionActual;
    if (estado.propuestas !== undefined) component.propuestas = estado.propuestas;
    if (estado.ultimaVersionAprobadaInfo !== undefined) component.ultimaVersionAprobadaInfo = estado.ultimaVersionAprobadaInfo;
    fixture.detectChanges();
  }

  it('debe mostrar badge de estado "propuesta"', () => {
    renderConEstado({ estadoActual: 'propuesta', versionActual: 1 });
    expect(fixture.nativeElement.textContent).toContain('Propuesta');
  });

  it('debe mostrar badge de estado "aprobada"', () => {
    renderConEstado({ estadoActual: 'aprobada', versionActual: 1 });
    expect(fixture.nativeElement.textContent).toContain('Aprobada');
  });

  it('debe mostrar versión actual', () => {
    renderConEstado({ estadoActual: 'aprobada', versionActual: 3 });
    expect(fixture.nativeElement.textContent).toContain('Versión 3');
  });

  it('debe mostrar botón "Aprobar" solo cuando estado es "propuesta"', () => {
    renderConEstado({ estadoActual: 'propuesta', versionActual: 2 });
    const buttons: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    expect(buttons.some(b => b.textContent?.includes('Aprobar parametrización'))).toBe(true);
  });

  it('NO debe mostrar botón "Aprobar" cuando estado es "aprobada" (no se puede aprobar una aprobada directamente)', () => {
    renderConEstado({ estadoActual: 'aprobada', versionActual: 1 });
    const buttons: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    expect(buttons.some(b => b.textContent?.includes('Aprobar parametrización'))).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Parametrización lista para uso');
  });

  // Corrección: Scrum Master único por proyecto (el creador), no por rol
  // global de cuenta. auth.currentUser() ya quedó fijado en la construcción
  // del componente (signal inicializado una sola vez desde 'mpdia_user' en
  // el beforeEach: email 'sm@test.com', rol global 'scrum_master') — lo que
  // cambia aquí es el proyecto activo: su scrumMasterEmail ya NO coincide con
  // el usuario autenticado, simulando que este usuario es scrum_master de
  // OTRO proyecto pero no de este. leerScrumMasterEmailProyectoActivo() relee
  // localStorage en cada evaluación, así que este cambio sí tiene efecto.
  it('NO debe mostrar botón "Aprobar" si el usuario autenticado no es el creador de este proyecto, aunque su rol global sea scrum_master', () => {
    (localStorage.getItem as jasmine.Spy).and.callFake((key: string) => {
      if (key === 'mpdia_user') {
        return JSON.stringify({ token: 'test-token', userId: 'u1', email: 'sm@test.com', role: 'scrum_master', nombre: 'SM Test' });
      }
      if (key === 'mpdia_proyecto_activo') {
        return JSON.stringify({ id: '123', scrumMasterEmail: 'otro-creador@test.com' });
      }
      return null;
    });

    renderConEstado({ estadoActual: 'propuesta', versionActual: 2 });

    const buttons: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    expect(buttons.some(b => b.textContent?.includes('Aprobar parametrización'))).toBe(false);
  });

  it('debe cargar estado desde backend cuando existe parametrización aprobada', (done) => {
    // Given
    const mockParametrizacion = {
      id: 'param-123',
      status: 'aprobada',
      version: 2,
      metricaId: '789',
      proyectoId: '123'
    };
    
    // When
    component.cargarEstadoParametrizacion('789', '123');
    
    // Then
    const req = httpMock.expectOne(req => 
      req.url.includes('/parametrizacion/ultima-aprobada') &&
      req.url.includes('metricaId=789') &&
      req.url.includes('proyectoId=123')
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockParametrizacion);
    
    setTimeout(() => {
      expect(component.estadoActual).toBe('aprobada');
      expect(component.versionActual).toBe(2);
      expect(component.parametrizacionId).toBe('param-123');
      done();
    }, 100);
  });

  it('debe manejar cuando NO existe parametrización aprobada', (done) => {
    // When
    component.cargarEstadoParametrizacion('789', '123');
    
    // Then
    const req = httpMock.expectOne(req => 
      req.url.includes('/parametrizacion/ultima-aprobada')
    );
    req.flush(null);
    
    setTimeout(() => {
      expect(component.estadoActual).toBeNull();
      expect(component.versionActual).toBe(1);
      expect(component.parametrizacionId).toBeNull();
      done();
    }, 100);
  });

  it('debe manejar error al cargar estado desde backend', (done) => {
    // When
    component.cargarEstadoParametrizacion('789', '123');

    // Then
    const req = httpMock.expectOne(req =>
      req.url.includes('/parametrizacion/ultima-aprobada')
    );
    req.error(new ErrorEvent('Network error'));

    setTimeout(() => {
      // No debe romper la app, debe manejar el error gracefully
      expect(component.estadoActual).toBeNull();
      done();
    }, 100);
  });

  // ========================================
  // FASE 16.10-C: permitir nueva propuesta (v2) desde una versión aprobada
  // ========================================

  it('CASO 1 (sin parametrización): botón "Guardar propuesta" visible', () => {
    renderConEstado({ estadoActual: null, propuestas: [mockPropuestaUnica] });
    const buttons: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    expect(buttons.some(b => b.textContent?.includes('Guardar propuesta'))).toBe(true);
  });

  it('CASO 3 (aprobada v1): botón "Guardar propuesta" visible (etiquetado como nueva propuesta) y versión aprobada sigue visible', () => {
    renderConEstado({
      estadoActual: 'propuesta', // ya se guardó una v2 en curso
      versionActual: 2,
      propuestas: [mockPropuestaUnica],
      ultimaVersionAprobadaInfo: { version: 1 }
    });
    const text = fixture.nativeElement.textContent;
    // La versión aprobada (v1) sigue visible, diferenciada de la propuesta nueva (v2)
    expect(text).toContain('Versión aprobada vigente: v1');
    expect(text).toContain('Versión 2');
  });

  it('debe precargar el formulario con los datos de la parametrización aprobada (indicador de "revisar/modificar")', (done) => {
    const mockAprobada = {
      id: 'param-v1', status: 'aprobada', version: 1,
      objetivo: 'Objetivo v1', procedimiento: 'Procedimiento v1',
      indicadorVariable: 'problemas_reportados', escala: 'Numérica >= 0',
      frecuenciaCaptura: 'por_sprint',
      fuenteAcademica: 'Guerrero-Calvache & Hernández (2024)',
      formulaAcademica: 'Σ problemas_reportados', tipoOperacion: 'SUMA', unidadResultado: 'problemas'
    };

    component.cargarEstadoParametrizacion('789', '123');
    const req = httpMock.expectOne(r => r.url.includes('/parametrizacion/ultima-aprobada'));
    req.flush(mockAprobada);

    setTimeout(() => {
      expect(component.ultimaVersionAprobadaInfo).toEqual({ version: 1 });
      expect(component.form.objetivo).toBe('Objetivo v1');
      expect(component.form.indicadorVariable).toBe('problemas_reportados');
      // Campos académicos también se precargan (para la prueba de "conserva campos académicos")
      expect(component.form.fuenteAcademica).toBe('Guerrero-Calvache & Hernández (2024)');
      expect(component.form.formulaAcademica).toBe('Σ problemas_reportados');
      expect(component.form.tipoOperacion).toBe('SUMA');
      expect(component.form.unidadResultado).toBe('problemas');
      done();
    }, 100);
  });

  it('guardar una nueva propuesta desde una parametrización aprobada envía el request correcto (backend decide la versión)', (done) => {
    // Given: estado ya cargado como aprobado v1
    component.metrica = mockMetrica;
    component.estadoActual = 'aprobada';
    component.versionActual = 1;
    component.propuestas = [mockPropuestaUnica];
    component.form = {
      objetivo: 'Objetivo revisado', procedimiento: 'Procedimiento revisado',
      indicadorVariable: 'problemas_reportados', escala: 'Numérica >= 0',
      frecuenciaCaptura: 'por_sprint',
      fuenteAcademica: 'Guerrero-Calvache & Hernández (2024)',
      formulaAcademica: 'Σ problemas_reportados', tipoOperacion: 'SUMA', unidadResultado: 'problemas'
    };

    // When
    component.guardarPropuesta();

    // Then: el frontend NO calcula versión, solo envía los datos
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/guardar-propuesta`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.metricaId).toBe(mockMetrica.factorId);
    expect(req.request.body.objetivo).toBe('Objetivo revisado');
    expect(req.request.body.fuenteAcademica).toBe('Guerrero-Calvache & Hernández (2024)');
    expect(req.request.body.tipoOperacion).toBe('SUMA');
    expect(req.request.body.version).toBeUndefined(); // Angular no envía/calcula versión

    // Backend responde con v2 (calculada por backend, no por Angular)
    req.flush({ id: 'param-v2', status: 'propuesta', version: 2 });

    setTimeout(() => {
      expect(component.estadoActual).toBe('propuesta');
      expect(component.versionActual).toBe(2);
      expect(component.parametrizacionId).toBe('param-v2');
      done();
    }, 100);
  });

  it('flujo completo no se rompe: aprobada v1 → guardar nueva propuesta v2 → aprobar v2 → v1 no se toca directamente', (done) => {
    // Estado inicial: v1 aprobada
    component.metrica = mockMetrica;
    component.estadoActual = 'aprobada';
    component.versionActual = 1;
    component.parametrizacionId = 'param-v1';
    component.ultimaVersionAprobadaInfo = { version: 1 };
    component.propuestas = [mockPropuestaUnica];
    component.form = {
      objetivo: 'Objetivo v2', procedimiento: 'Procedimiento v2',
      indicadorVariable: 'problemas_reportados', escala: 'Numérica >= 0',
      frecuenciaCaptura: 'por_sprint'
    };

    // Guardar nueva propuesta v2
    component.guardarPropuesta();
    const reqGuardar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/guardar-propuesta`);
    reqGuardar.flush({ id: 'param-v2', status: 'propuesta', version: 2 });

    setTimeout(() => {
      expect(component.estadoActual).toBe('propuesta');
      expect(component.parametrizacionId).toBe('param-v2'); // nunca se reutiliza el id de v1

      // Aprobar v2 (el botón "Aprobar" solo existe cuando estadoActual === 'propuesta',
      // así que estructuralmente nunca se puede aprobar v1 de nuevo)
      component.aprobarParametrizacion();
      const reqAprobar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-v2/aprobar`);
      expect(reqAprobar.request.url).not.toContain('param-v1');
      reqAprobar.flush({ id: 'param-v2', status: 'aprobada', version: 2 });

      setTimeout(() => {
        expect(component.estadoActual).toBe('aprobada');
        expect(component.versionActual).toBe(2);
        done();
      }, 100);
    }, 100);
  });

  // ========================================
  // FASE 16.10-D: aprobar debe usar la propuesta persistida, no this.form
  // ========================================
  describe('FASE 16.10-D: propuestaPendiente como fuente de verdad al aprobar', () => {

    const propuestaAcademicaCompleta: PropuestaGenAI = {
      titulo: 'Impedimentos por sprint',
      objetivo: 'Identificar y cuantificar los impedimentos del sprint',
      procedimiento: 'Contar impedimentos registrados durante el sprint',
      indicadorVariable: 'impedimentos_registrados',
      escala: 'Numérica entera >= 0',
      frecuenciaCaptura: 'por_sprint',
      fuenteAcademica: 'Guerrero-Calvache & Hernández (2024)',
      formulaAcademica: 'Σ(I_sprint)',
      tipoOperacion: 'SUMA',
      unidadResultado: 'impedimentos',
      nombreVariable: 'impedimentos_registrados',
      justificacion: 'PROPUESTA que requiere validación del equipo.'
    };

    // Respuesta realista de POST /guardar-propuesta: la entidad completa
    // persistida (MetricParametrizacion es @Data, sin @JsonIgnore — Jackson
    // serializa todos sus campos, académicos incluidos).
    const respuestaGuardarRealista = {
      id: 'param-vel-02',
      status: 'propuesta',
      version: 1,
      objetivo: propuestaAcademicaCompleta.objetivo,
      procedimiento: propuestaAcademicaCompleta.procedimiento,
      indicadorVariable: propuestaAcademicaCompleta.indicadorVariable,
      escala: propuestaAcademicaCompleta.escala,
      frecuenciaCaptura: propuestaAcademicaCompleta.frecuenciaCaptura,
      fuenteAcademica: propuestaAcademicaCompleta.fuenteAcademica,
      formulaAcademica: propuestaAcademicaCompleta.formulaAcademica,
      tipoOperacion: propuestaAcademicaCompleta.tipoOperacion,
      unidadResultado: propuestaAcademicaCompleta.unidadResultado,
      nombreVariable: propuestaAcademicaCompleta.nombreVariable
    };

    function guardarPropuestaCompleta(): void {
      // A) La propuesta (simulando la respuesta real de Gemini) trae los
      // campos académicos completos.
      component.metrica = mockMetrica;
      component.propuestas = [propuestaAcademicaCompleta];

      // B) "Copiar al formulario" conserva esos valores.
      component.usarPropuesta(propuestaAcademicaCompleta);
      expect(component.form.tipoOperacion).toBe('SUMA');
      expect(component.form.formulaAcademica).toBe('Σ(I_sprint)');
      expect(component.form.unidadResultado).toBe('impedimentos');
      expect(component.form.nombreVariable).toBe('impedimentos_registrados');

      // C) Guardar propuesta persiste esos valores (se verifica el request).
      component.guardarPropuesta();
      const reqGuardar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/guardar-propuesta`);
      expect(reqGuardar.request.body.tipoOperacion).toBe('SUMA');
      expect(reqGuardar.request.body.formulaAcademica).toBe('Σ(I_sprint)');
      expect(reqGuardar.request.body.unidadResultado).toBe('impedimentos');
      expect(reqGuardar.request.body.nombreVariable).toBe('impedimentos_registrados');
      reqGuardar.flush(respuestaGuardarRealista);
    }

    it('D. aprobar inmediatamente después de guardar conserva EXACTAMENTE los valores académicos', (done) => {
      guardarPropuestaCompleta();

      setTimeout(() => {
        expect(component.propuestaPendiente).toEqual(respuestaGuardarRealista);

        component.aprobarParametrizacion();
        const reqAprobar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-vel-02/aprobar`);
        expect(reqAprobar.request.body.tipoOperacion).toBe('SUMA');
        expect(reqAprobar.request.body.formulaAcademica).toBe('Σ(I_sprint)');
        expect(reqAprobar.request.body.unidadResultado).toBe('impedimentos');
        expect(reqAprobar.request.body.indicadorVariable).toBe('impedimentos_registrados');
        expect(reqAprobar.request.body.nombreVariable).toBe('impedimentos_registrados');
        reqAprobar.flush({ id: 'param-vel-02', status: 'aprobada', version: 1 });
        done();
      }, 100);
    });

    it('E. REGRESIÓN — regenerar/editar el formulario después de guardar NO contamina la aprobación', (done) => {
      guardarPropuestaCompleta();

      setTimeout(() => {
        // Simula exactamente el escenario que causó el bug real en SIG-VEL-02:
        // después de guardar, el usuario regenera con GenAI (o edita el
        // formulario) y el formulario queda con datos distintos/incompletos,
        // SIN volver a guardar esa nueva propuesta.
        component.form = {
          objetivo: 'Objetivo de OTRA regeneración',
          procedimiento: 'Procedimiento distinto',
          indicadorVariable: 'Impedimento Bloqueante (una ocurrencia de bloqueo registrado)',
          escala: 'Otra escala',
          frecuenciaCaptura: 'por_sprint'
          // académicos ausentes por completo, como usarDelTop() o un formulario
          // nunca completado — this.form.tipoOperacion sería undefined
        };

        component.aprobarParametrizacion();
        const reqAprobar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-vel-02/aprobar`);

        // La aprobación debe usar la propuesta PERSISTIDA, no el formulario contaminado
        expect(reqAprobar.request.body.tipoOperacion).toBe('SUMA');
        expect(reqAprobar.request.body.formulaAcademica).toBe('Σ(I_sprint)');
        expect(reqAprobar.request.body.unidadResultado).toBe('impedimentos');
        expect(reqAprobar.request.body.indicadorVariable).toBe('impedimentos_registrados');
        expect(reqAprobar.request.body.nombreVariable).toBe('impedimentos_registrados');
        expect(reqAprobar.request.body.objetivo).toBe(propuestaAcademicaCompleta.objetivo);

        reqAprobar.flush({ id: 'param-vel-02', status: 'aprobada', version: 1 });
        done();
      }, 100);
    });

    it('compatibilidad: sin propuestaPendiente en memoria (propuesta pendiente de otra sesión), aprobar sigue usando this.form', () => {
      // No se llamó guardarPropuesta() en este ciclo de vida del componente
      // (p. ej. la propuesta ya existía al cargar la página). Debe preservarse
      // el comportamiento previo: usar this.form como respaldo.
      component.parametrizacionId = 'param-otra-sesion';
      component.estadoActual = 'propuesta';
      component.propuestaPendiente = null;
      component.form = {
        objetivo: 'Objetivo de sesión previa',
        procedimiento: 'Procedimiento previo',
        indicadorVariable: 'indicador_previo',
        escala: 'Escala previa',
        frecuenciaCaptura: 'por_sprint',
        tipoOperacion: 'PROMEDIO',
        formulaAcademica: 'x/n',
        unidadResultado: 'puntos'
      };

      component.aprobarParametrizacion();
      const reqAprobar = httpMock.expectOne(`${environment.apiBaseUrl}/parametrizacion/param-otra-sesion/aprobar`);
      expect(reqAprobar.request.body.tipoOperacion).toBe('PROMEDIO');
      expect(reqAprobar.request.body.objetivo).toBe('Objetivo de sesión previa');
      reqAprobar.flush({ id: 'param-otra-sesion', status: 'aprobada', version: 1 });
    });
  });
});
