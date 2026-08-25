// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AICopilotComponent } from './ai-copilot.component';
import { AICopilotService } from '../../services/ai-copilot.service';
import { ChatResponse } from '../../models/ai-copilot.model';

/**
 * FASE 12.9/14 — Tests del menú de sugerencias tras una respuesta fuera de dominio y tras
 * un error real del backend/Gemini.
 *
 * El texto usado para simular la respuesta del guardrail debe coincidir EXACTAMENTE con
 * CopilotDomainGuard.RESPUESTA_FUERA_DE_DOMINIO (backend) y con la constante homónima en
 * ai-copilot.component.ts — es la señal que el componente usa para decidir si debe volver
 * a mostrar el menú.
 */
const RESPUESTA_FUERA_DE_DOMINIO =
  'Soy el AI Agile Copilot de MPDIA. Puedo ayudarte con el sprint, métricas, riesgos, ' +
  'problemas, impedimentos, tendencias y retrospectivas del proyecto activo.';

function fueraDeDominioResponse(): ChatResponse {
  return {
    message: RESPUESTA_FUERA_DE_DOMINIO,
    toolsUsed: [],
    timestamp: new Date().toISOString(),
    hasData: false
  };
}

function respuestaValidaResponse(texto: string): ChatResponse {
  return {
    message: texto,
    toolsUsed: [],
    timestamp: new Date().toISOString(),
    hasData: true
  };
}

describe('AICopilotComponent — FASE 12.9 menú tras pregunta fuera de dominio', () => {
  let component: AICopilotComponent;
  let fixture: ComponentFixture<AICopilotComponent>;
  let copilotServiceSpy: jasmine.SpyObj<AICopilotService>;

  beforeEach(async () => {
    copilotServiceSpy = jasmine.createSpyObj('AICopilotService', ['chat']);

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proyecto-1', nombre: 'Proyecto de prueba' }));
    localStorage.setItem('mpdia_sprint_activo', JSON.stringify({ id: 'sprint-1', numero: 1 }));

    await TestBed.configureTestingModule({
      imports: [AICopilotComponent],
      providers: [{ provide: AICopilotService, useValue: copilotServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(AICopilotComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem('mpdia_proyecto_activo');
    localStorage.removeItem('mpdia_sprint_activo');
  });

  function enviarMensaje(texto: string): void {
    component.userInput = texto;
    component.sendMessage();
    fixture.detectChanges();
  }

  // 1. Respuesta fuera de dominio muestra el menú.
  it('tras una respuesta fuera de dominio, shouldShowQuickPrompts vuelve a ser true', () => {
    copilotServiceSpy.chat.and.returnValue(of(fueraDeDominioResponse()));

    enviarMensaje('¿cuánto es 2 más 2?');

    expect(component.shouldShowQuickPrompts).toBeTrue();
  });

  // 2. El menú contiene las opciones existentes.
  it('el menú reutiliza las opciones ya existentes (quickPrompts)', () => {
    expect(component.quickPrompts).toEqual([
      '¿Cómo estuvo el último sprint?',
      'Analiza el sprint activo',
      '¿Qué riesgos detectas?',
      '¿Qué deberíamos revisar en la retrospectiva?',
      'Comparar los últimos sprints'
    ]);
  });

  // 3. Las opciones son clicables: useQuickPrompt() debe enviar esa pregunta normalmente.
  it('useQuickPrompt() envía la pregunta correspondiente a través del servicio', () => {
    copilotServiceSpy.chat.and.returnValue(of(respuestaValidaResponse('Respuesta real.')));

    component.useQuickPrompt('¿Qué riesgos detectas?');
    fixture.detectChanges();

    expect(copilotServiceSpy.chat).toHaveBeenCalledWith(
      jasmine.objectContaining({ message: '¿Qué riesgos detectas?' })
    );
  });

  // 4. Una pregunta fuera de dominio no dispara ningún flujo adicional: solo UNA llamada,
  // el mensaje se muestra tal cual (sin marcarlo como error) y no se reintenta.
  it('una respuesta fuera de dominio se muestra como mensaje normal (no como error) y sin llamadas adicionales', () => {
    copilotServiceSpy.chat.and.returnValue(of(fueraDeDominioResponse()));

    enviarMensaje('cuánto es dos por dos');

    expect(copilotServiceSpy.chat).toHaveBeenCalledTimes(1);
    const ultimoMensaje = component.messages[component.messages.length - 1];
    expect(ultimoMensaje.content).toBe(RESPUESTA_FUERA_DE_DOMINIO);
    expect(ultimoMensaje.error).toBeFalsy();
  });

  // 5. El menú no se duplica al recibir varias respuestas fuera de dominio seguidas.
  it('varias preguntas fuera de dominio seguidas no duplican el menú', () => {
    copilotServiceSpy.chat.and.returnValue(of(fueraDeDominioResponse()));

    enviarMensaje('¿cuánto es 2 más 2?');
    enviarMensaje('cuéntame un chiste');
    enviarMensaje('cuánto es dos por dos');

    // shouldShowQuickPrompts es un booleano derivado del ÚLTIMO mensaje: nunca "se acumula".
    expect(component.shouldShowQuickPrompts).toBeTrue();

    const bloquesDeMenu = fixture.nativeElement.querySelectorAll('.quick-prompts');
    expect(bloquesDeMenu.length).toBe(1);
  });

  // 6. Un error real del backend sigue mostrando el mensaje de error correspondiente,
  // marcado como error (FASE 14 — BLOQUE 3: el menú SÍ vuelve a aparecer tras un error,
  // para que el usuario no quede sin forma de retomar la conversación; ver tests dedicados
  // más abajo).
  it('un error real del backend muestra el mensaje de error correspondiente', () => {
    copilotServiceSpy.chat.and.returnValue(
      throwError(() => new Error('El AI Copilot no está disponible en este momento'))
    );

    enviarMensaje('¿qué riesgos detectas?');

    const ultimoMensaje = component.messages[component.messages.length - 1];
    expect(ultimoMensaje.error).toBeTrue();
    expect(ultimoMensaje.content).toContain('no está disponible');
  });

  // ══════════════════════════════════════════════════════════════════════════════
  // FASE 14 — BLOQUE 3: manejo de error inicial/posterior y recuperación del menú.
  // ══════════════════════════════════════════════════════════════════════════════

  // 1. Error inicial (primera interacción del usuario termina en error).
  it('un error como primera interacción no rompe la interfaz y marca el mensaje como error', () => {
    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));

    enviarMensaje('¿qué riesgos detectas?');

    expect(component.messages.length).toBeGreaterThan(0);
    const ultimoMensaje = component.messages[component.messages.length - 1];
    expect(ultimoMensaje.error).toBeTrue();
    expect(component.loading()).toBeFalse();
  });

  // 2. Error después de una pregunta válida previa.
  it('un error después de una respuesta válida se muestra correctamente sin perder el historial previo', () => {
    copilotServiceSpy.chat.and.returnValue(of(respuestaValidaResponse('Todo bien por ahora.')));
    enviarMensaje('¿qué riesgos detectas?');

    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));
    enviarMensaje('¿cómo estuvo el último sprint?');

    const ultimoMensaje = component.messages[component.messages.length - 1];
    expect(ultimoMensaje.error).toBeTrue();
    // El mensaje válido anterior sigue presente en el historial (no se pierde).
    expect(component.messages.some(m => m.content === 'Todo bien por ahora.')).toBeTrue();
  });

  // 3. Se puede seguir preguntando normalmente después de un error.
  it('permite enviar una nueva pregunta normalmente después de un error', () => {
    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));
    enviarMensaje('¿qué riesgos detectas?');

    copilotServiceSpy.chat.and.returnValue(of(respuestaValidaResponse('Ahora sí funciona.')));
    enviarMensaje('¿cómo estuvo el último sprint?');

    expect(copilotServiceSpy.chat).toHaveBeenCalledTimes(2);
    const ultimoMensaje = component.messages[component.messages.length - 1];
    expect(ultimoMensaje.content).toBe('Ahora sí funciona.');
    expect(ultimoMensaje.error).toBeFalsy();
  });

  // 4. El menú vuelve a aparecer después de un error (para que el usuario no quede "atascado").
  it('el menú de sugerencias reaparece después de un error', () => {
    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));

    enviarMensaje('¿qué riesgos detectas?');

    expect(component.shouldShowQuickPrompts).toBeTrue();
    const menu = fixture.nativeElement.querySelector('.quick-prompts');
    expect(menu).toBeTruthy();
  });

  // 5. Varias preguntas tras un error no duplican el menú.
  it('varias preguntas después de un error no duplican el menú', () => {
    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));
    enviarMensaje('¿qué riesgos detectas?');

    copilotServiceSpy.chat.and.returnValue(throwError(() => new Error('servidor no disponible')));
    enviarMensaje('¿cómo estuvo el último sprint?');
    enviarMensaje('¿qué deberíamos mejorar?');

    expect(component.shouldShowQuickPrompts).toBeTrue();
    const bloquesDeMenu = fixture.nativeElement.querySelectorAll('.quick-prompts');
    expect(bloquesDeMenu.length).toBe(1);
  });
});
