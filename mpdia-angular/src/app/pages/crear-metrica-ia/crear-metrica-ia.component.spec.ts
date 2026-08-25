// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 15 — Tests de "Crear métrica con IA"
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CrearMetricaIAComponent } from './crear-metrica-ia.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaIAService } from '../../services/metrica-ia.service';
import { SeleccionService } from '../../services/seleccion.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { MetricaIAPropuestaDto, MetricaIACreadaDto, MetricaExistenteDto, PosibleDuplicadoDto } from '../../models/metrica-ia.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

function propuesta(overrides: Partial<MetricaIAPropuestaDto> = {}): MetricaIAPropuestaDto {
  return {
    nombre: 'Estado de ánimo del equipo',
    descripcion: 'Mide el clima emocional del equipo durante el sprint',
    objetivo: 'Detectar señales tempranas de desgaste',
    queMide: 'Percepción subjetiva promedio del equipo',
    variablesSugeridas: 'animo_promedio',
    tipoOperacionSugerido: 'PROMEDIO',
    formulaSugerida: 'PROMEDIO(animo_promedio)',
    unidadResultado: 'escala 1-5',
    fuenteSugerida: 'No determinado',
    ...overrides
  };
}

describe('CrearMetricaIAComponent', () => {
  let component: CrearMetricaIAComponent;
  let fixture: ComponentFixture<CrearMetricaIAComponent>;
  let metricaIAServiceSpy: jasmine.SpyObj<MetricaIAService>;
  let seleccionServiceSpy: jasmine.SpyObj<SeleccionService>;
  let planeacionServiceSpy: jasmine.SpyObj<PlaneacionService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    metricaIAServiceSpy = jasmine.createSpyObj('MetricaIAService', ['generarPropuesta', 'crear']);
    seleccionServiceSpy = jasmine.createSpyObj('SeleccionService', ['agregar', 'getSnapshot', 'parametrizar']);
    planeacionServiceSpy = jasmine.createSpyObj('PlaneacionService', ['seleccionar']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proyecto-1', nombre: 'Proyecto de prueba' }));

    await TestBed.configureTestingModule({
      imports: [CrearMetricaIAComponent],
      providers: [
        { provide: MetricaIAService, useValue: metricaIAServiceSpy },
        { provide: SeleccionService, useValue: seleccionServiceSpy },
        { provide: PlaneacionService, useValue: planeacionServiceSpy },
        { provide: Router, useValue: routerSpy },
      ]
    })
      .overrideComponent(CrearMetricaIAComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(CrearMetricaIAComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // 1. Generar propuesta no persiste nada.
  it('generarPropuesta llama solo al servicio de propuesta, nunca crea nada', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));

    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    expect(metricaIAServiceSpy.generarPropuesta).toHaveBeenCalledWith('Quiero medir el estado de ánimo del equipo');
    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
    expect(seleccionServiceSpy.agregar).not.toHaveBeenCalled();
    expect(component.paso).toBe('revision');
  });

  // 2. La propuesta se muestra rotulada como propuesta y precarga el formulario editable.
  it('tras generar la propuesta, el formulario editable queda precargado con los datos de la IA', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));

    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    expect(component.edit.nombre).toBe('Estado de ánimo del equipo');
    expect(component.edit.tipoOperacionSugerido).toBe('PROMEDIO');
  });

  // 3. Error al generar: se muestra el error y no avanza de paso.
  it('si falla la generación de la propuesta, muestra error y permanece en el paso de necesidad', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(throwError(() => new Error('fallo')));

    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    expect(component.error).toContain('No se pudo generar');
    expect(component.paso).toBe('necesidad');
  });

  // 4. Cancelar no persiste nada.
  it('cancelar descarta la propuesta sin llamar a crear ni a seleccionar', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    component.cancelar();

    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
    expect(seleccionServiceSpy.agregar).not.toHaveBeenCalled();
    expect(component.paso).toBe('necesidad');
    expect(component.propuesta).toBeNull();
  });

  // 5. La edición humana es la que se envía a crear, no la propuesta original.
  it('usarPropuesta envía exactamente los datos editados por el Scrum Master, no los originales de la IA', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    // El Scrum Master edita nombre y descripción.
    component.edit.nombre = 'Clima del equipo (editado)';
    component.edit.descripcion = 'Descripción editada por el Scrum Master';

    const creada: MetricaIACreadaDto = {
      metricaId: 'metrica-nueva-1', codigo: 'IA-001',
      nombre: 'Clima del equipo (editado)', proyectoId: 'proyecto-1'
    };
    metricaIAServiceSpy.crear.and.returnValue(of(creada));
    seleccionServiceSpy.getSnapshot.and.returnValue([{
      id: 'sel-1', factorId: 'metrica-nueva-1', factorNombre: 'x', factorCategoria: 'Significado',
      metricaNombre: 'x', metricaDescripcion: '', proyectoId: 'proyecto-1',
      estadoParametrizacion: 'sin_parametrizar', creadoEn: new Date().toISOString()
    }] as any);

    component.usarPropuesta();

    expect(metricaIAServiceSpy.crear).toHaveBeenCalledWith({
      proyectoId: 'proyecto-1',
      categoriaId: 1,
      nombre: 'Clima del equipo (editado)',
      descripcion: 'Descripción editada por el Scrum Master',
      objetivo: 'Detectar señales tempranas de desgaste',
      queMide: 'Percepción subjetiva promedio del equipo',
      variablesSugeridas: 'animo_promedio',
      confirmarCreacionDiferente: false
    });
  });

  // 6. Tras confirmar, se asocia al proyecto (vía flujo existente) y navega a parametrización.
  it('tras crear la métrica, la asocia al proyecto y navega al flujo existente de parametrización', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    const creada: MetricaIACreadaDto = {
      metricaId: 'metrica-nueva-1', codigo: 'IA-001',
      nombre: 'Estado de ánimo del equipo', proyectoId: 'proyecto-1'
    };
    metricaIAServiceSpy.crear.and.returnValue(of(creada));
    seleccionServiceSpy.getSnapshot.and.returnValue([{
      id: 'sel-1', factorId: 'metrica-nueva-1', factorNombre: 'x', factorCategoria: 'Significado',
      metricaNombre: 'x', metricaDescripcion: '', proyectoId: 'proyecto-1',
      estadoParametrizacion: 'sin_parametrizar', creadoEn: new Date().toISOString()
    }] as any);

    component.usarPropuesta();

    expect(seleccionServiceSpy.agregar).toHaveBeenCalledWith(jasmine.objectContaining({
      factorId: 'metrica-nueva-1', proyectoId: 'proyecto-1'
    }));
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/parametrizacion', 'sel-1']);
  });

  // 7. Error al crear (ej. sin permiso): no navega, muestra mensaje específico.
  it('si falla la creación por falta de permiso, muestra el mensaje correspondiente y no navega', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    metricaIAServiceSpy.crear.and.returnValue(throwError(() => ({ status: 403 })));

    component.usarPropuesta();

    expect(component.error).toContain('permiso');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // 8. Sin proyecto activo no se puede confirmar ninguna propuesta.
  it('sin proyecto activo, usarPropuesta no llama al servicio de creación', () => {
    localStorage.removeItem('mpdia_proyecto_activo');
    component.proyecto = null;

    component.usarPropuesta();

    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
  });

  // ── CASO B: la métrica ya existe en el catálogo global ──────────────────

  function metricaExistente(overrides: Partial<MetricaExistenteDto> = {}): MetricaExistenteDto {
    return {
      id: 'metrica-existente-1', codigo: 'SIG-XX-01', nombre: 'Estado de ánimo del equipo',
      descripcion: 'Ya existía', categoria: 'Significado',
      ...overrides
    };
  }

  it('CASO B: si el backend responde 409 con metricaExistente, la muestra en vez de un error genérico', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    const existente = metricaExistente();
    metricaIAServiceSpy.crear.and.returnValue(
      throwError(() => ({ status: 409, error: { tipo: 'duplicado_exacto', error: 'Ya existe', metricaExistente: existente } }))
    );

    component.usarPropuesta();

    expect(component.metricaExistente).toEqual(existente);
    expect(component.error).toBe('');
    expect(seleccionServiceSpy.agregar).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // CASO B: reutilizar la métrica existente asocia (seleccionar), NO crea duplicado.
  it('usarMetricaExistente asocia la métrica existente al proyecto y navega a Planeación, sin crear una copia', () => {
    component.metricaExistente = metricaExistente();
    planeacionServiceSpy.seleccionar.and.returnValue(of(undefined));

    component.usarMetricaExistente();

    expect(planeacionServiceSpy.seleccionar).toHaveBeenCalledWith('proyecto-1', 'metrica-existente-1');
    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/planeacion']);
  });

  it('usarMetricaExistente: si falla la asociación, muestra error y no navega', () => {
    component.metricaExistente = metricaExistente();
    planeacionServiceSpy.seleccionar.and.returnValue(throwError(() => ({ status: 500 })));

    component.usarMetricaExistente();

    expect(component.reutilizarError).toContain('No se pudo asociar');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // ── FASE 23: CASO C — posible(s) duplicado(s) CONCEPTUAL(es) ────────────

  function posibleDuplicado(overrides: Partial<PosibleDuplicadoDto> = {}): PosibleDuplicadoDto {
    return {
      metrica: metricaExistente({ id: 'metrica-conceptual-1', nombre: 'Estado de ánimo del equipo' }),
      score: 62,
      razones: ['descripción/intención muy similar', 'misma categoría'],
      ...overrides
    };
  }

  // 8. El aviso de posible duplicado conceptual se muestra correctamente.
  it('CASO C: si el backend responde 409 con posible_duplicado, muestra los candidatos y sus razones', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el clima emocional del equipo';
    component.generarPropuesta();

    const candidatos = [posibleDuplicado()];
    metricaIAServiceSpy.crear.and.returnValue(
      throwError(() => ({ status: 409, error: { tipo: 'posible_duplicado', candidatos } }))
    );

    component.usarPropuesta();

    expect(component.posiblesDuplicados).toEqual(candidatos);
    expect(component.metricaExistente).toBeNull();
    expect(component.error).toBe('');
    expect(seleccionServiceSpy.agregar).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // 2 (requerido). La alerta muestra el nombre de la métrica existente en el DOM.
  it('CASO C: la alerta renderizada muestra el nombre de la métrica existente', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el clima emocional del equipo';
    component.generarPropuesta();

    metricaIAServiceSpy.crear.and.returnValue(
      throwError(() => ({ status: 409, error: { tipo: 'posible_duplicado', candidatos: [posibleDuplicado()] } }))
    );

    component.usarPropuesta();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Estado de ánimo del equipo');
  });

  // 9. "Reutilizar existente" continúa el flujo con el ID de la métrica ya existente.
  it('CASO C: reutilizarPosibleDuplicado asocia la métrica existente al proyecto, sin crear una copia', () => {
    const candidato = posibleDuplicado();
    component.posiblesDuplicados = [candidato];
    planeacionServiceSpy.seleccionar.and.returnValue(of(undefined));

    component.reutilizarPosibleDuplicado(candidato);

    expect(planeacionServiceSpy.seleccionar).toHaveBeenCalledWith('proyecto-1', 'metrica-conceptual-1');
    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
    expect(component.posiblesDuplicados).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/planeacion']);
  });

  // 10. "Crear como métrica diferente" permite crear la nueva métrica igual.
  it('CASO C: crearComoDiferente reenvía la creación con confirmarCreacionDiferente=true', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el clima emocional del equipo';
    component.generarPropuesta();

    const creada: MetricaIACreadaDto = {
      metricaId: 'metrica-nueva-2', codigo: 'IA-002',
      nombre: component.edit.nombre, proyectoId: 'proyecto-1'
    };
    metricaIAServiceSpy.crear.and.returnValue(of(creada));
    seleccionServiceSpy.getSnapshot.and.returnValue([{
      id: 'sel-2', factorId: 'metrica-nueva-2', factorNombre: 'x', factorCategoria: 'Significado',
      metricaNombre: 'x', metricaDescripcion: '', proyectoId: 'proyecto-1',
      estadoParametrizacion: 'sin_parametrizar', creadoEn: new Date().toISOString()
    }] as any);

    component.crearComoDiferente();

    expect(metricaIAServiceSpy.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      confirmarCreacionDiferente: true
    }));
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/parametrizacion', 'sel-2']);
  });

  // 6 (requerido). 409 duplicado_exacto muestra el mensaje específico de "ya existe en el catálogo".
  it('CASO B: la alerta renderizada indica que la métrica ya existe en el catálogo', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    metricaIAServiceSpy.crear.and.returnValue(
      throwError(() => ({ status: 409, error: { tipo: 'duplicado_exacto', error: 'Ya existe', metricaExistente: metricaExistente() } }))
    );

    component.usarPropuesta();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Esta métrica ya existe en el catálogo');
    // El caso de nombre EXACTO no debe ofrecer "Crear como diferente": solo reutilizar o cancelar.
    expect(texto).not.toContain('Crear como métrica diferente');
  });

  // 7 (requerido). Cualquier otro error (sin tipo reconocido) conserva el mensaje genérico.
  it('otros errores (sin tipo posible_duplicado/duplicado_exacto) muestran el mensaje genérico', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el estado de ánimo del equipo';
    component.generarPropuesta();

    metricaIAServiceSpy.crear.and.returnValue(throwError(() => ({ status: 500, error: { error: 'boom' } })));

    component.usarPropuesta();

    expect(component.error).toBe('No se pudo crear la métrica. Intenta nuevamente.');
    expect(component.metricaExistente).toBeNull();
    expect(component.posiblesDuplicados).toBeNull();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // 11. "Cancelar" no modifica nada: ni crea, ni asocia, ni conserva el aviso.
  it('CASO C: cancelar tras el aviso de posible duplicado no crea ni asocia nada', () => {
    metricaIAServiceSpy.generarPropuesta.and.returnValue(of(propuesta()));
    component.necesidad = 'Quiero medir el clima emocional del equipo';
    component.generarPropuesta();
    component.posiblesDuplicados = [posibleDuplicado()];

    component.cancelar();

    expect(component.posiblesDuplicados).toBeNull();
    expect(component.paso).toBe('necesidad');
    expect(metricaIAServiceSpy.crear).not.toHaveBeenCalled();
    expect(planeacionServiceSpy.seleccionar).not.toHaveBeenCalled();
    expect(seleccionServiceSpy.agregar).not.toHaveBeenCalled();
  });
});
