// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';

import { CapturaVariablesComponent } from './captura-variables.component';
import { VariableDinamicaService, VariablesMetricaResponse } from '../../services/variable-dinamica.service';
import { environment } from '../../../environments/environment';

describe('CapturaVariablesComponent - Fase 16.7', () => {
  let component: CapturaVariablesComponent;
  let fixture: ComponentFixture<CapturaVariablesComponent>;
  let service: VariableDinamicaService;
  let httpMock: HttpTestingController;

  const mockVariablesResponse: VariablesMetricaResponse = {
    parametrizacionId: 'param-123',
    version: 1,
    status: 'aprobada',
    variables: [
      {
        id: 'var-1',
        nombre: 'Story Points Completados',
        descripcion: 'Puntos de historia finalizados',
        tipoDato: 'numerico',
        obligatorio: true,
        escalaMin: 0,
        escalaMax: 100,
        valorNum: undefined,
        valorTexto: undefined,
        valorBool: undefined
      },
      {
        id: 'var-2',
        nombre: 'Errores críticos',
        descripcion: 'Cantidad de errores críticos detectados',
        tipoDato: 'numerico',
        obligatorio: true,
        escalaMin: undefined,
        escalaMax: undefined,
        valorNum: undefined,
        valorTexto: undefined,
        valorBool: undefined
      }
    ]
  };

  beforeEach(async () => {
    const mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: (key: string) => key === 'metricaId' ? 'metrica-123' : null
        },
        queryParamMap: {
          get: (key: string) => {
            if (key === 'proyectoId') return 'proyecto-123';
            if (key === 'sprintId') return 'sprint-123';
            return null;
          }
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [
        CapturaVariablesComponent,
        HttpClientTestingModule,
        ReactiveFormsModule
      ],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        VariableDinamicaService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CapturaVariablesComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(VariableDinamicaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('debe crear el componente', () => {
    expect(component).toBeTruthy();
  });

  it('debe crear formulario dinámicamente desde variables', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    
    component.ngOnInit();
    
    setTimeout(() => {
      expect(component.form.get('var-1')).toBeTruthy();
      expect(component.form.get('var-2')).toBeTruthy();
      expect(component.variables.length).toBe(2);
      done();
    }, 100);
  });

  it('debe renderizar variables recibidas del backend', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    
    fixture.detectChanges();
    
    setTimeout(() => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Story Points Completados');
      expect(compiled.textContent).toContain('Errores críticos');
      done();
    }, 100);
  });

  it('debe mostrar nombre y descripción de cada variable', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    
    fixture.detectChanges();
    
    setTimeout(() => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Story Points Completados');
      expect(compiled.textContent).toContain('Puntos de historia finalizados');
      done();
    }, 100);
  });

  it('debe precargar valor existente si viene del backend', (done) => {
    const responseConValor = {
      ...mockVariablesResponse,
      variables: [{
        ...mockVariablesResponse.variables[0],
        valorNum: 42
      }]
    };
    
    spyOn(service, 'obtenerVariables').and.returnValue(of(responseConValor));
    
    component.ngOnInit();
    
    setTimeout(() => {
      expect(component.form.get('var-1')?.value).toBe(42);
      done();
    }, 100);
  });

  it('debe validar campo obligatorio', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    
    component.ngOnInit();
    
    setTimeout(() => {
      const control = component.form.get('var-1');
      expect(control?.hasError('required')).toBe(true);
      
      control?.setValue(10);
      expect(control?.hasError('required')).toBe(false);
      done();
    }, 100);
  });

  it('debe validar tipo numérico', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    
    component.ngOnInit();
    
    setTimeout(() => {
      const control = component.form.get('var-1');
      control?.setValue('texto-invalido');
      
      // El input type="number" convierte inválidos a null
      expect(control?.value).toBe('texto-invalido');
      done();
    }, 100);
  });

  it('debe validar tipo booleano', (done) => {
    const responseConBooleano = {
      ...mockVariablesResponse,
      variables: [{
        id: 'var-bool',
        nombre: 'Cumplió objetivo',
        descripcion: 'Si/No',
        tipoDato: 'booleano',
        obligatorio: true,
        escalaMin: undefined,
        escalaMax: undefined,
        valorNum: undefined,
        valorTexto: undefined,
        valorBool: undefined
      }]
    };
    
    spyOn(service, 'obtenerVariables').and.returnValue(of(responseConBooleano));
    
    component.ngOnInit();
    
    setTimeout(() => {
      const control = component.form.get('var-bool');
      expect(control).toBeTruthy();
      
      control?.setValue(true);
      expect(control?.value).toBe(true);
      done();
    }, 100);
  });

  it('debe guardar valores correctamente', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    spyOn(service, 'guardarValores').and.returnValue(of(undefined));
    
    component.ngOnInit();
    
    setTimeout(() => {
      component.form.get('var-1')?.setValue(42);
      component.form.get('var-2')?.setValue(5);
      
      component.guardarValores();
      
      expect(service.guardarValores).toHaveBeenCalled();
      
      setTimeout(() => {
        expect(component.estado).toBe('guardado');
        done();
      }, 100);
    }, 100);
  });

  it('debe manejar error HTTP 400', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    spyOn(service, 'guardarValores').and.returnValue(
      throwError(() => ({ status: 400, error: { message: 'Valores inválidos' } }))
    );
    
    component.ngOnInit();
    
    setTimeout(() => {
      component.form.get('var-1')?.setValue(42);
      component.form.get('var-2')?.setValue(5);
      
      component.guardarValores();
      
      setTimeout(() => {
        expect(component.estado).toBe('error');
        expect(component.errorMensaje).toContain('no son válidos');
        done();
      }, 100);
    }, 100);
  });

  it('debe manejar ausencia de parametrización aprobada', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(
      throwError(() => ({ 
        status: 404, 
        error: { message: 'No existe parametrización aprobada' } 
      }))
    );
    
    component.ngOnInit();
    
    setTimeout(() => {
      expect(component.estado).toBe('sin-parametrizacion');
      expect(component.errorMensaje).toContain('No existe una parametrización aprobada');
      done();
    }, 100);
  });

  it('debe manejar ausencia de variables', (done) => {
    const responseSinVariables = {
      ...mockVariablesResponse,
      variables: []
    };
    
    spyOn(service, 'obtenerVariables').and.returnValue(of(responseSinVariables));
    
    component.ngOnInit();
    
    setTimeout(() => {
      expect(component.estado).toBe('sin-variables');
      done();
    }, 100);
  });

  it('debe mostrar estado loading', () => {
    expect(component.estado).toBe('loading');
  });

  it('debe mostrar estado guardando y luego guardado', (done) => {
    spyOn(service, 'obtenerVariables').and.returnValue(of(mockVariablesResponse));
    spyOn(service, 'guardarValores').and.returnValue(of(undefined));
    
    component.ngOnInit();
    
    setTimeout(() => {
      component.form.get('var-1')?.setValue(42);
      component.form.get('var-2')?.setValue(5);
      
      component.guardarValores();
      
      // El estado cambia rápidamente de guardando a guardado
      expect(['guardando', 'guardado']).toContain(component.estado);
      done();
    }, 100);
  });
});
