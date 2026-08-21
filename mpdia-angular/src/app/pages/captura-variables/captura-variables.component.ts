// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { 
  VariableDinamicaService, 
  VariableConValor, 
  VariablesMetricaResponse,
  GuardarValoresRequest
} from '../../services/variable-dinamica.service';
import { ShellComponent } from '../../layout/shell/shell.component';

type EstadoUI = 'loading' | 'ready' | 'guardando' | 'guardado' | 'error' | 
                'sin-parametrizacion' | 'sin-variables' | 'empty' |
                'calculando' | 'calculado';

@Component({
  selector: 'app-captura-variables',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ShellComponent],
  templateUrl: './captura-variables.component.html'
})
export class CapturaVariablesComponent implements OnInit {

  metricaId: string | null = null;
  proyectoId: string | null = null;
  sprintId: string | null = null;
  
  estado: EstadoUI = 'loading';
  errorMensaje = '';
  
  parametrizacionId: string | null = null;
  version: number | null = null;
  variables: VariableConValor[] = [];
  
  // Resultado del cálculo
  resultadoCalculado: number | null = null;
  resultadoUnidad: string | null = null;
  
  form: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private variableService: VariableDinamicaService
  ) {
    this.form = this.fb.group({});
  }

  ngOnInit(): void {
    // Obtener parámetros de la ruta
    this.metricaId = this.route.snapshot.paramMap.get('metricaId');
    this.proyectoId = this.route.snapshot.queryParamMap.get('proyectoId');
    this.sprintId = this.route.snapshot.queryParamMap.get('sprintId');
    
    if (!this.metricaId || !this.proyectoId || !this.sprintId) {
      this.estado = 'error';
      this.errorMensaje = 'Faltan parámetros requeridos (métrica, proyecto, sprint)';
      return;
    }
    
    this.cargarVariables();
  }

  cargarVariables(): void {
    this.estado = 'loading';
    
    this.variableService.obtenerVariables(
      this.metricaId!,
      this.proyectoId!,
      this.sprintId!
    ).subscribe({
      next: (response: VariablesMetricaResponse) => {
        this.parametrizacionId = response.parametrizacionId;
        this.version = response.version;
        this.variables = response.variables;
        
        if (this.variables.length === 0) {
          this.estado = 'sin-variables';
        } else {
          this.construirFormulario();
          this.estado = 'ready';
        }
      },
      error: (error) => {
        console.error('Error cargando variables:', error);
        
        if (error.status === 404 || error.error?.message?.includes('No existe parametrización aprobada')) {
          this.estado = 'sin-parametrizacion';
          this.errorMensaje = 'No existe una parametrización aprobada para esta métrica. ' +
                             'Debe aprobar una parametrización antes de capturar valores.';
        } else if (error.status === 403) {
          this.estado = 'error';
          this.errorMensaje = 'No tienes permisos para registrar valores en este proyecto.';
        } else {
          this.estado = 'error';
          this.errorMensaje = error.error?.message || 'Error al cargar las variables de la métrica.';
        }
      }
    });
  }

  construirFormulario(): void {
    const group: any = {};
    
    this.variables.forEach(variable => {
      const validators = [];
      
      if (variable.obligatorio) {
        validators.push(Validators.required);
      }
      
      // Valor inicial: el existente o null
      let valorInicial = null;
      if (variable.tipoDato === 'numerico') {
        valorInicial = variable.valorNum;
      } else if (variable.tipoDato === 'texto') {
        valorInicial = variable.valorTexto;
      } else if (variable.tipoDato === 'booleano') {
        valorInicial = variable.valorBool;
      }
      
      group[variable.id] = [valorInicial, validators];
    });
    
    this.form = this.fb.group(group);
  }

  guardarValores(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    
    this.estado = 'guardando';
    
    const valores = this.variables.map(variable => {
      const valor = this.form.get(variable.id)?.value;
      
      return {
        variableId: variable.id,
        valorNum: variable.tipoDato === 'numerico' ? (valor !== null && valor !== '' ? Number(valor) : undefined) : undefined,
        valorTexto: variable.tipoDato === 'texto' ? (valor || undefined) : undefined,
        valorBool: variable.tipoDato === 'booleano' ? (valor ?? undefined) : undefined,
        observacion: undefined
      };
    }).filter(v => v.valorNum !== undefined || v.valorTexto !== undefined || v.valorBool !== undefined);
    
    const request: GuardarValoresRequest = {
      proyectoId: this.proyectoId!,
      sprintId: this.sprintId!,
      valores: valores
    };
    
    this.variableService.guardarValores(this.metricaId!, request).subscribe({
      next: () => {
        this.estado = 'guardado';
        setTimeout(() => {
          if (this.estado === 'guardado') {
            this.estado = 'ready';
          }
        }, 3000);
      },
      error: (error) => {
        console.error('Error guardando valores:', error);
        this.estado = 'error';
        
        if (error.status === 400) {
          this.errorMensaje = 'Los valores ingresados no son válidos. ' +
                             (error.error?.message || 'Verifica los datos e intenta nuevamente.');
        } else if (error.status === 403) {
          this.errorMensaje = 'No tienes permisos para registrar valores en este proyecto.';
        } else if (error.status === 404) {
          this.errorMensaje = 'No se encontró una parametrización o sprint válido.';
        } else {
          this.errorMensaje = 'No fue posible guardar los valores. Inténtalo nuevamente.';
        }
      }
    });
  }

  volver(): void {
    this.router.navigate(['/planeacion']);
  }
  
  calcularMetrica(): void {
    if (!this.metricaId || !this.proyectoId || !this.sprintId) {
      return;
    }
    
    this.estado = 'calculando';
    this.errorMensaje = '';
    
    this.variableService.calcularMetrica(
      this.metricaId,
      this.proyectoId,
      this.sprintId
    ).subscribe({
      next: (resultado) => {
        this.resultadoCalculado = resultado.resultado;
        this.resultadoUnidad = resultado.unidad || null;
        this.estado = 'calculado';
      },
      error: (error) => {
        console.error('Error calculando métrica:', error);
        this.estado = 'error';
        
        if (error.error?.error === 'DATOS_INVALIDOS') {
          this.errorMensaje = 'Datos insuficientes para calcular la métrica.';
        } else if (error.error?.error === 'ERROR_ARITMETICO') {
          this.errorMensaje = 'Error en el cálculo: ' + (error.error?.mensaje || 'División por cero o error aritmético');
        } else {
          this.errorMensaje = 'No fue posible calcular la métrica. Inténtalo nuevamente.';
        }
      }
    });
  }

  getErrorMessage(variableId: string): string {
    const control = this.form.get(variableId);
    if (control?.hasError('required')) {
      return 'Este campo es obligatorio';
    }
    return '';
  }
}
