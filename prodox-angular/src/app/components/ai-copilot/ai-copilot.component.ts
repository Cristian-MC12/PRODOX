// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AICopilotService } from '../../services/ai-copilot.service';
import { ChatMessage } from '../../models/ai-copilot.model';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';

@Component({
  selector: 'app-ai-copilot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-copilot.component.html',
  styleUrls: ['./ai-copilot.component.css']
})
export class AICopilotComponent implements OnInit, AfterViewChecked {
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  messages: ChatMessage[] = [];
  userInput = '';
  loading = signal(false);
  error = signal<string | null>(null);
  isOpen = signal(false);
  proyecto: ProyectoDto | null = null;
  sprint: SprintDto | null = null;

  private shouldScroll = false;

  // FASE 12.9: alineadas con las opciones que ya se anuncian en el mensaje de bienvenida
  // (addWelcomeMessage) — un solo texto "real" para el menú, tanto narrado como clicable.
  quickPrompts = [
    '¿Cómo estuvo el último sprint?',
    'Analiza el sprint activo',
    '¿Qué riesgos detectas?',
    '¿Qué deberíamos revisar en la retrospectiva?',
    'Comparar los últimos sprints'
  ];

  /** Debe coincidir exactamente con CopilotDomainGuard.RESPUESTA_FUERA_DE_DOMINIO (backend). */
  private static readonly RESPUESTA_FUERA_DE_DOMINIO =
    'Soy el AI Agile Copilot de PRODOX. Puedo ayudarte con el sprint, métricas, riesgos, ' +
    'problemas, impedimentos, tendencias y retrospectivas del proyecto activo.';

  constructor(
    private copilotService: AICopilotService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.loadContext();
    this.addWelcomeMessage();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  private loadContext(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;

      const s = localStorage.getItem('mpdia_sprint_activo');
      this.sprint = s ? JSON.parse(s) : null;

      // Validar que el proyecto tenga estructura válida
      if (this.proyecto && (!this.proyecto.id || !this.proyecto.nombre)) {
        console.warn('Proyecto en localStorage tiene estructura inválida');
        this.proyecto = null;
        localStorage.removeItem('mpdia_proyecto_activo');
      }

      // Validar que el sprint tenga estructura válida
      if (this.sprint && (!this.sprint.id || !this.sprint.numero)) {
        console.warn('Sprint en localStorage tiene estructura inválida');
        this.sprint = null;
        localStorage.removeItem('mpdia_sprint_activo');
      }
    } catch (e) {
      console.error('Error al cargar contexto:', e);
      // Limpiar localStorage corrupto
      localStorage.removeItem('mpdia_proyecto_activo');
      localStorage.removeItem('mpdia_sprint_activo');
      this.proyecto = null;
      this.sprint = null;
    }
  }

  private addWelcomeMessage(): void {
    this.messages.push({
      role: 'assistant',
      content: `Hola, soy tu **AI Agile Copilot**.

Puedo ayudarte a analizar la productividad de tu equipo, sprints y métricas.

Puedes preguntarme, por ejemplo:
• ¿Cómo estuvo el último sprint?
• Analiza el sprint activo
• ¿Qué riesgos detectas?
• ¿Qué deberíamos revisar en la retrospectiva?
• Comparar los últimos sprints`,
      timestamp: new Date()
    });
  }

  sendMessage(): void {
    const message = this.userInput.trim();
    if (!message || this.loading()) return;

    if (!this.proyecto) {
      this.error.set('Selecciona un proyecto primero');
      return;
    }

    // Agregar mensaje del usuario
    this.messages.push({
      role: 'user',
      content: message,
      timestamp: new Date()
    });

    this.userInput = '';
    this.loading.set(true);
    this.error.set(null);
    this.shouldScroll = true;

    // Agregar mensaje de loading
    const loadingMsg: ChatMessage = {
      role: 'assistant',
      content: 'Analizando...',
      timestamp: new Date(),
      loading: true
    };
    this.messages.push(loadingMsg);
    this.shouldScroll = true;

    // Enviar al backend
    this.copilotService.chat({
      message,
      proyectoId: this.proyecto.id,
      sprintId: this.sprint?.id ?? null
    }).subscribe({
      next: (response) => {
        // Remover mensaje de loading
        this.messages = this.messages.filter(m => !m.loading);

        // Agregar respuesta
        this.messages.push({
          role: 'assistant',
          content: response.message,
          timestamp: new Date(response.timestamp)
        });

        this.loading.set(false);
        this.shouldScroll = true;
      },
      error: (err) => {
        // Remover mensaje de loading
        this.messages = this.messages.filter(m => !m.loading);

        // Determinar mensaje de error más específico
        let errorMessage = err.message || 'Ocurrió un error inesperado';
        
        // Mensajes específicos según el tipo de error
        if (err.message?.includes('límite de consultas') || err.message?.includes('límite')) {
          errorMessage = '⏱️ Has alcanzado el límite temporal de consultas. Intenta en unos minutos.';
        } else if (err.message?.includes('acceso') || err.message?.includes('autorización')) {
          errorMessage = '⚠️ No tienes acceso a este proyecto. Selecciona un proyecto válido.';
        } else if (err.message?.includes('no encontrado') || err.message?.includes('encontrado')) {
          errorMessage = '⚠️ El proyecto o sprint seleccionado no existe. Actualiza la página.';
        } else if (err.message?.includes('sesión')) {
          errorMessage = '⚠️ Tu sesión ha expirado. Por favor, inicia sesión nuevamente.';
        } else if (err.message?.includes('servidor') || err.message?.includes('disponible')) {
          errorMessage = '⚠️ El AI Copilot no está disponible en este momento. Intenta más tarde.';
        }

        // Agregar mensaje de error
        this.messages.push({
          role: 'assistant',
          content: errorMessage,
          timestamp: new Date(),
          error: true
        });

        this.error.set(errorMessage);
        this.loading.set(false);
        this.shouldScroll = true;
      }
    });
  }

  /**
   * FASE 12.9/14: el menú de sugerencias se muestra al inicio (sin conversación), inmediatamente
   * después de una respuesta fuera de dominio, y también tras un error real del backend/Gemini
   * (FASE 14 — BLOQUE 3: un error no debe dejar al usuario sin forma de retomar la conversación).
   * Al depender siempre del ÚLTIMO mensaje, nunca se duplica ni se acumula aunque el usuario
   * encadene varias preguntas fuera de dominio o varios errores seguidos.
   */
  get shouldShowQuickPrompts(): boolean {
    if (this.loading()) return false;
    if (this.messages.length === 1) return true;
    const last = this.messages[this.messages.length - 1];
    if (last.role !== 'assistant') return false;
    return this.isOutOfDomainMessage(last) || !!last.error;
  }

  private isOutOfDomainMessage(msg: ChatMessage): boolean {
    return msg.role === 'assistant' &&
           !msg.error &&
           msg.content === AICopilotComponent.RESPUESTA_FUERA_DE_DOMINIO;
  }

  useQuickPrompt(prompt: string): void {
    this.userInput = prompt;
    this.sendMessage();
  }

  toggle(): void {
    this.isOpen.update(v => !v);
  }

  close(): void {
    this.isOpen.set(false);
  }

  private scrollToBottom(): void {
    try {
      this.messagesContainer.nativeElement.scrollTop = 
        this.messagesContainer.nativeElement.scrollHeight;
    } catch (e) {
      // Ignore
    }
  }

  /**
   * Formatea el mensaje con Markdown básico y sanitiza HTML para prevenir XSS
   */
  formatMessage(content: string): SafeHtml {
    // Primero escapar HTML para prevenir XSS
    const escaped = content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
    
    // Luego aplicar formato markdown básico (ya escapado)
    const formatted = escaped
      // Títulos
      .replace(/^### (.*$)/gim, '<h6>$1</h6>')
      .replace(/^## (.*$)/gim, '<h5>$1</h5>')
      .replace(/^# (.*$)/gim, '<h4>$1</h4>')
      // Negritas
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      // Listas
      .replace(/^\* (.+)$/gim, '<li>$1</li>')
      .replace(/^• (.+)$/gim, '<li>$1</li>')
      // Saltos de línea
      .replace(/\n/g, '<br>');
    
    // Sanitizar el resultado con DomSanitizer
    return this.sanitizer.sanitize(1, formatted) || '';
  }
}
