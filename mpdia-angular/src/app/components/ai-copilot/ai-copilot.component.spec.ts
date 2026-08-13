import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { AICopilotComponent } from './ai-copilot.component';
import { AICopilotService } from '../../services/ai-copilot.service';
import { of, throwError } from 'rxjs';

describe('AICopilotComponent', () => {
  let component: AICopilotComponent;
  let fixture: ComponentFixture<AICopilotComponent>;
  let service: AICopilotService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AICopilotComponent, HttpClientTestingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(AICopilotComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(AICopilotService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show welcome message on init', () => {
    expect(component.messages.length).toBe(1);
    expect(component.messages[0].role).toBe('assistant');
    expect(component.messages[0].content).toContain('AI Agile Copilot');
  });

  it('should toggle panel', () => {
    expect(component.isOpen()).toBe(false);
    component.toggle();
    expect(component.isOpen()).toBe(true);
    component.toggle();
    expect(component.isOpen()).toBe(false);
  });

  it('should close panel', () => {
    component.isOpen.set(true);
    component.close();
    expect(component.isOpen()).toBe(false);
  });

  it('should not send empty message', () => {
    const initialLength = component.messages.length;
    component.userInput = '   ';
    component.sendMessage();
    expect(component.messages.length).toBe(initialLength);
  });

  it('should send message and receive response', (done) => {
    // Mock proyecto
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'test-id', nombre: 'Test' }));
    component.ngOnInit();

    const mockResponse = {
      message: 'Respuesta del copilot',
      toolsUsed: ['getActiveSprintMetrics'],
      timestamp: new Date().toISOString(),
      hasData: true
    };

    spyOn(service, 'chat').and.returnValue(of(mockResponse));

    component.userInput = 'Test message';
    component.sendMessage();

    // Esperar a que se procese
    setTimeout(() => {
      expect(component.messages.some(m => m.content === 'Test message')).toBe(true);
      expect(component.messages.some(m => m.content === mockResponse.message)).toBe(true);
      expect(component.loading()).toBe(false);
      done();
    }, 100);
  });

  it('should handle error', (done) => {
    // Mock proyecto
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'test-id', nombre: 'Test' }));
    component.ngOnInit();

    spyOn(service, 'chat').and.returnValue(throwError(() => new Error('Error de prueba')));

    component.userInput = 'Test message';
    component.sendMessage();

    setTimeout(() => {
      expect(component.loading()).toBe(false);
      expect(component.error()).toBe('Error de prueba');
      done();
    }, 100);
  });

  it('should use quick prompt', () => {
    // Mock proyecto
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'test-id', nombre: 'Test' }));
    component.ngOnInit();

    spyOn(component, 'sendMessage');
    const prompt = 'Analiza el sprint activo';
    
    component.useQuickPrompt(prompt);
    
    expect(component.userInput).toBe(prompt);
    expect(component.sendMessage).toHaveBeenCalled();
  });

  it('should show error if no proyecto selected', () => {
    localStorage.removeItem('mpdia_proyecto_activo');
    component.ngOnInit();

    component.userInput = 'Test message';
    component.sendMessage();

    expect(component.error()).toBe('Selecciona un proyecto primero');
  });

  it('should format message with markdown', () => {
    const input = '**Hola**\n• Item 1\n• Item 2';
    const output = component.formatMessage(input);
    
    expect(output).toContain('<strong>Hola</strong>');
    expect(output).toContain('<li>Item 1</li>');
    expect(output).toContain('<br>');
  });

  afterEach(() => {
    localStorage.clear();
  });

  // CO.4 — SUGERENCIAS DE PREGUNTAS
  describe('CO.4 - Sugerencias de preguntas', () => {
    it('debería tener lista de quickPrompts', () => {
      expect(component.quickPrompts).toBeDefined();
      expect(component.quickPrompts.length).toBeGreaterThan(0);
    });

    it('debería incluir pregunta "¿Cómo está mi equipo?"', () => {
      expect(component.quickPrompts).toContain('¿Cómo está mi equipo?');
    });

    it('debería incluir pregunta "¿Qué riesgos detectas?"', () => {
      expect(component.quickPrompts).toContain('¿Qué riesgos detectas?');
    });

    it('debería incluir pregunta "Analiza las métricas del sprint actual"', () => {
      expect(component.quickPrompts).toContain('Analiza las métricas del sprint actual');
    });

    it('debería incluir pregunta "¿Qué debería mejorar el equipo?"', () => {
      expect(component.quickPrompts).toContain('¿Qué debería mejorar el equipo?');
    });

    it('debería usar quick prompt correctamente', () => {
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'test-id', nombre: 'Test' }));
      component.ngOnInit();
      spyOn(component, 'sendMessage');

      const prompt = component.quickPrompts[0];
      component.useQuickPrompt(prompt);

      expect(component.userInput).toBe(prompt);
      expect(component.sendMessage).toHaveBeenCalled();
    });

    it('debería desactivar quick prompts si no hay proyecto', () => {
      localStorage.removeItem('mpdia_proyecto_activo');
      component.ngOnInit();

      expect(component.proyecto).toBeNull();
    });
  });
});
