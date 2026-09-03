// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { timeboxAbreviado, timeboxPalabraCompleta } from './timebox.model';

describe('timebox.model', () => {
  describe('timeboxAbreviado', () => {
    it('HORAS: "8 h"', () => {
      expect(timeboxAbreviado({ timeBoxSemanas: 1, timeboxUnidad: 'HORAS', timeboxDuracion: 8 })).toBe('8 h');
    });

    it('DIAS: "3 días"', () => {
      expect(timeboxAbreviado({ timeBoxSemanas: 1, timeboxUnidad: 'DIAS', timeboxDuracion: 3 })).toBe('3 días');
    });

    it('SEMANAS: "2 sem"', () => {
      expect(timeboxAbreviado({ timeBoxSemanas: 2, timeboxUnidad: 'SEMANAS', timeboxDuracion: 2 })).toBe('2 sem');
    });

    it('fallback: sin timeboxUnidad/timeboxDuracion (objeto cacheado antes de V41), usa timeBoxSemanas', () => {
      expect(timeboxAbreviado({ timeBoxSemanas: 2 })).toBe('2 sem');
    });
  });

  describe('timeboxPalabraCompleta', () => {
    it('HORAS: "8 hora(s)"', () => {
      expect(timeboxPalabraCompleta({ timeBoxSemanas: 1, timeboxUnidad: 'HORAS', timeboxDuracion: 8 })).toBe('8 hora(s)');
    });

    it('DIAS: "3 día(s)"', () => {
      expect(timeboxPalabraCompleta({ timeBoxSemanas: 1, timeboxUnidad: 'DIAS', timeboxDuracion: 3 })).toBe('3 día(s)');
    });

    it('SEMANAS: "2 semana(s)" — idéntico al texto histórico', () => {
      expect(timeboxPalabraCompleta({ timeBoxSemanas: 2, timeboxUnidad: 'SEMANAS', timeboxDuracion: 2 })).toBe('2 semana(s)');
    });

    it('fallback: sin timeboxUnidad/timeboxDuracion, usa timeBoxSemanas', () => {
      expect(timeboxPalabraCompleta({ timeBoxSemanas: 2 })).toBe('2 semana(s)');
    });
  });
});
