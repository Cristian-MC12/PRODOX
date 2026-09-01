// Autor: Cristian Santiago Martinez Cordoba — PRODOX

/**
 * Request para enviar mensaje al AI Copilot
 */
export interface ChatRequest {
  message: string;
  proyectoId: string;
  sprintId?: string | null;
}

/**
 * Response del AI Copilot
 */
export interface ChatResponse {
  message: string;
  toolsUsed: string[];
  timestamp: string;
  hasData: boolean;
}

/**
 * Mensaje en la conversación (local, para UI)
 */
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  loading?: boolean;
  error?: boolean;
}
